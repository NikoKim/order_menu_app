package com.order.menu.module.entertainment.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.module.entertainment.core.dto.AiConnectionDtos;
import java.net.URI;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import javax.net.ssl.SSLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GameAiConnectionService {
    private final ObjectMapper objectMapper;
    private final GameAiCredentialService credentials;
    private final EntertainmentGameService games;
    private final GameRoomService rooms;
    private final HttpClient client;
    private final Map<String, Long> recentChecks = new LinkedHashMap<>();

    @Autowired
    public GameAiConnectionService(ObjectMapper objectMapper, GameAiCredentialService credentials,
            EntertainmentGameService games, GameRoomService rooms) {
        this(objectMapper, credentials, games, rooms, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build());
    }

    GameAiConnectionService(ObjectMapper objectMapper, GameAiCredentialService credentials,
            EntertainmentGameService games, GameRoomService rooms, HttpClient client) {
        this.objectMapper = objectMapper;
        this.credentials = credentials;
        this.games = games;
        this.rooms = rooms;
        this.client = client;
    }

    public AiConnectionDtos.Result test(PlayerIdentity player, AiConnectionDtos.Request selection) {
        if ("HOSTED".equals(selection.source())) {
            if (selection.gameId() == null) bad("请选择需要验证的游戏配置");
            games.requirePlayable(selection.gameId());
        }
        if ("ROOM".equals(selection.source())) {
            if (selection.roomId() == null) bad("请选择需要验证的房间配置");
            if (!rooms.get(selection.roomId(), player).host()) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "只有房主可以验证房间 AI 配置");
            }
        }
        var credential = credentials.credentialForConnectionTest(player, selection.source(),
                selection.gameId(), selection.roomId(), selection.aiCredential());
        requirePermit(player.key());
        if ("HOSTED".equals(selection.source())) requirePermit("GAME:" + selection.gameId());
        long started = System.nanoTime();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", credential.model());
            body.put("messages", List.of(Map.of("role", "user", "content", "Reply with OK.")));
            boolean deepSeek = isDeepSeek(credential);
            body.put(deepSeek ? "max_tokens" : "max_completion_tokens", 16);
            if (deepSeek) body.put("thinking", Map.of("type", "disabled"));
            body.put("stream", false);
            HttpResponse<String> response = send(credential, body);
            if (response.statusCode() == 400 && body.containsKey("max_completion_tokens")
                    && unsupportedCompletionLimit(response.body())) {
                body.remove("max_completion_tokens");
                body.put("max_tokens", 16);
                response = send(credential, body);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return result(false, statusMessage(response.statusCode()), credential.model(), started);
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choice = root.path("choices").path(0);
            boolean valid = choice.path("message").isObject()
                    && (choice.path("message").path("content").isTextual()
                        || root.path("usage").path("completion_tokens").asInt() > 0);
            return result(valid, valid ? "连接有效，模型已响应" : "接口已响应，但不是有效的聊天模型响应", credential.model(), started);
        } catch (HttpConnectTimeoutException exception) {
            return result(false, "服务器连接模型接口超时，请检查服务器网络和接口地址", credential.model(), started);
        } catch (HttpTimeoutException exception) {
            return result(false, "模型响应超时，请稍后重试；这不代表 API Key 无效", credential.model(), started);
        } catch (UnknownHostException exception) {
            return result(false, "服务器无法解析模型接口域名，请检查地址和服务器 DNS", credential.model(), started);
        } catch (ConnectException exception) {
            return result(false, "服务器无法连接模型接口，请检查服务器网络、DNS 或代理设置", credential.model(), started);
        } catch (SSLException exception) {
            return result(false, "模型接口的安全连接失败，请检查服务器证书和 HTTPS 代理设置", credential.model(), started);
        } catch (JsonProcessingException exception) {
            return result(false, "接口已响应，但返回的不是有效 JSON，请检查接口地址是否为聊天 API", credential.model(), started);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return result(false, "连接验证已取消", credential.model(), started);
        } catch (Exception exception) {
            // Never return upstream bodies or exception messages: they may contain credentials.
            return result(false, "连接失败，请检查接口地址、网络和模型配置", credential.model(), started);
        }
    }

    private HttpResponse<String> send(GameAiCredentialService.DecryptedCredential credential,
            Map<String, Object> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(credential.baseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(isDeepSeek(credential) ? 30 : 20)).header("Authorization", "Bearer " + credential.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
        return client.send(request, ignored -> boundedResponse());
    }

    private static boolean isDeepSeek(GameAiCredentialService.DecryptedCredential credential) {
        return "api.deepseek.com".equalsIgnoreCase(URI.create(credential.baseUrl()).getHost());
    }

    private static HttpResponse.BodySubscriber<String> boundedResponse() {
        var delegate = HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
        return new HttpResponse.BodySubscriber<>() {
            private Flow.Subscription subscription;
            private long size;
            private boolean failed;
            public CompletionStage<String> getBody() { return delegate.getBody(); }
            public void onSubscribe(Flow.Subscription value) { subscription = value; delegate.onSubscribe(value); }
            public void onNext(List<ByteBuffer> buffers) {
                if (failed) return;
                size += buffers.stream().mapToLong(ByteBuffer::remaining).sum();
                if (size > 65536) { failed = true; subscription.cancel(); delegate.onError(new IllegalStateException("Response too large")); }
                else delegate.onNext(buffers);
            }
            public void onError(Throwable error) { if (!failed) delegate.onError(error); }
            public void onComplete() { if (!failed) delegate.onComplete(); }
        };
    }

    private boolean unsupportedCompletionLimit(String body) {
        try {
            JsonNode error = objectMapper.readTree(body).path("error");
            return error.path("param").asText().equals("max_completion_tokens")
                    || error.path("message").asText().contains("max_completion_tokens");
        } catch (Exception ignored) { return false; }
    }

    private synchronized void requirePermit(String key) {
        long now = System.currentTimeMillis();
        recentChecks.entrySet().removeIf(entry -> now - entry.getValue() > 60000);
        if (recentChecks.containsKey(key) && now - recentChecks.get(key) < 20000) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "请间隔 20 秒后再验证，避免重复产生模型费用");
        }
        if (recentChecks.size() >= 2048) recentChecks.remove(recentChecks.keySet().iterator().next());
        recentChecks.put(key, now);
    }

    private static AiConnectionDtos.Result result(boolean connected, String message, String model, long started) {
        return new AiConnectionDtos.Result(connected, message, model, Math.max(0, (System.nanoTime() - started) / 1000000));
    }

    private static String statusMessage(int status) {
        return switch (status) {
            case 401 -> "API Key 无效或已过期";
            case 402 -> "模型账户余额不足，请到服务商控制台检查余额";
            case 403 -> "该 API Key 没有访问此模型的权限";
            case 404 -> "接口路径或模型不存在，请核对配置";
            case 429 -> "模型额度不足或请求过于频繁，请检查服务商配额";
            case 400, 422 -> "模型未接受测试请求，请核对模型名称及接口兼容性";
            default -> status >= 300 && status < 400 ? "接口发生跳转，请填写最终的 HTTPS 接口地址" : "模型服务暂时不可用，请稍后重试";
        };
    }

    private static void bad(String message) { throw new BusinessException(ErrorCode.BAD_REQUEST, message); }
}
