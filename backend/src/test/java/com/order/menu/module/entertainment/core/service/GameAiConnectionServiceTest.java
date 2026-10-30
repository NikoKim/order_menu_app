package com.order.menu.module.entertainment.core.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.module.entertainment.core.dto.AiConnectionDtos;
import com.order.menu.module.entertainment.core.dto.EntertainmentDtos;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Flow;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GameAiConnectionServiceTest {
    private final GameAiCredentialService credentials = mock(GameAiCredentialService.class);
    private final EntertainmentGameService games = mock(EntertainmentGameService.class);
    private final GameRoomService rooms = mock(GameRoomService.class);
    private final HttpClient client = mock(HttpClient.class);
    private final PlayerIdentity player = new PlayerIdentity("GUEST", null, 1L, "G:1", "访客");
    private final AiConnectionDtos.Request own = new AiConnectionDtos.Request("OWN", null, null,
            new EntertainmentDtos.AiCredentialRequest("https://example.com/v1", "test-model", "test-secret-key"));
    private GameAiConnectionService service;

    @BeforeEach
    void setup() {
        service = new GameAiConnectionService(new ObjectMapper(), credentials, games, rooms, client);
        when(credentials.credentialForConnectionTest(any(), anyString(), any(), any(), any()))
                .thenReturn(new GameAiCredentialService.DecryptedCredential("https://example.com/v1", "test-model", "test-secret-key", "G:1"));
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int status, String body) {
        return mock(HttpResponse.class, invocation -> switch (invocation.getMethod().getName()) {
            case "statusCode" -> status;
            case "body" -> body;
            default -> RETURNS_DEFAULTS.answer(invocation);
        });
    }

    @Test
    void validatesActualChatResponseWithoutReturningOrPersistingTheSecret() throws Exception {
        when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response(200,
                "{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}"));
        var result = service.test(player, own);
        assertTrue(result.connected());
        assertEquals("test-model", result.model());
        assertFalse(new ObjectMapper().writeValueAsString(result).contains("test-secret-key"));
        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(), any());
        assertEquals("https://example.com/v1/chat/completions", request.getValue().uri().toString());
        assertEquals("Bearer test-secret-key", request.getValue().headers().firstValue("Authorization").orElseThrow());
        assertEquals(20, request.getValue().timeout().orElseThrow().toSeconds());
        assertEquals(16, requestBody(request.getValue()).path("max_completion_tokens").asInt());
    }

    private com.fasterxml.jackson.databind.JsonNode requestBody(HttpRequest request) throws Exception {
        var subscriber = HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription subscription) { subscriber.onSubscribe(subscription); }
            public void onNext(ByteBuffer buffer) { subscriber.onNext(List.of(buffer)); }
            public void onError(Throwable error) { subscriber.onError(error); }
            public void onComplete() { subscriber.onComplete(); }
        });
        return new ObjectMapper().readTree(subscriber.getBody().toCompletableFuture().join());
    }

    @Test
    void deepSeekUsesItsDocumentedTokenLimitAndDisablesDefaultThinking() throws Exception {
        for (String url : List.of("https://api.deepseek.com", "https://api.deepseek.com/v1")) {
            var isolated = new GameAiConnectionService(new ObjectMapper(), credentials, games, rooms, client);
            when(credentials.credentialForConnectionTest(any(), anyString(), any(), any(), any()))
                    .thenReturn(new GameAiCredentialService.DecryptedCredential(url, "deepseek-flash", "test-secret-key", "G:1"));
            when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response(200,
                    "\n\n{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}"));
            clearInvocations(client);
            assertTrue(isolated.test(player, own).connected());
            var request = ArgumentCaptor.forClass(HttpRequest.class);
            verify(client).send(request.capture(), any());
            var body = requestBody(request.getValue());
            assertEquals(url + "/chat/completions", request.getValue().uri().toString());
            assertEquals("deepseek-flash", body.path("model").asText());
            assertEquals(16, body.path("max_tokens").asInt());
            assertFalse(body.has("max_completion_tokens"));
            assertEquals("disabled", body.path("thinking").path("type").asText());
            assertFalse(body.path("stream").asBoolean());
            assertEquals(30, request.getValue().timeout().orElseThrow().toSeconds());
        }
    }

    @Test
    void deepSeekLookalikeHostsDoNotReceiveProviderSpecificParameters() throws Exception {
        when(credentials.credentialForConnectionTest(any(), anyString(), any(), any(), any()))
                .thenReturn(new GameAiCredentialService.DecryptedCredential("https://api.deepseek.com.example.org/v1",
                        "deepseek-flash", "test-secret-key", "G:1"));
        when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response(200,
                "{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}"));
        assertTrue(service.test(player, own).connected());
        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(), any());
        assertFalse(requestBody(request.getValue()).has("thinking"));
    }

    @Test
    void rejectsAnHtmlSuccessPageAndDoesNotEchoUpstreamErrors() throws Exception {
        when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response(200, "<html>test-secret-key</html>"));
        var result = service.test(player, own);
        assertFalse(result.connected());
        assertFalse(result.message().contains("test-secret-key"));
    }

    @Test
    void reportsAuthenticationAndQuotaFailuresSafely() throws Exception {
        for (int status : List.of(401, 402, 403, 404, 429, 302, 500)) {
            var isolated = new GameAiConnectionService(new ObjectMapper(), credentials, games, rooms, client);
            when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response(status, "test-secret-key"));
            var result = isolated.test(player, own);
            assertFalse(result.connected());
            assertFalse(result.message().contains("test-secret-key"));
            if (status == 402) assertTrue(result.message().contains("余额不足"));
        }
    }

    @Test
    void supportsOlderCompatibleCompletionTokenParameter() throws Exception {
        when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(
                response(400, "{\"error\":{\"param\":\"max_completion_tokens\"}}"),
                response(200, "{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}"));
        assertTrue(service.test(player, own).connected());
        var requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, times(2)).send(requests.capture(), any());
        assertEquals(16, requestBody(requests.getAllValues().get(1)).path("max_tokens").asInt());
        assertFalse(requestBody(requests.getAllValues().get(1)).has("max_completion_tokens"));
    }

    @Test
    void sharedHostedChecksAreBoundedEvenAcrossDifferentGuests() throws Exception {
        when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response(200,
                "{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}"));
        var selection = new AiConnectionDtos.Request("HOSTED", 10L, null, null);
        assertTrue(service.test(player, selection).connected());
        var guest = new PlayerIdentity("GUEST", null, 2L, "G:2", "访客2");
        assertThrows(BusinessException.class, () -> service.test(guest, selection));
        verify(client).send(any(), any());
        verify(games, times(2)).requirePlayable(10L);
    }

    @Test
    void timeoutsAreReportedAsFailures() throws Exception {
        when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenThrow(new HttpTimeoutException("secret"));
        assertFalse(service.test(player, own).connected());
    }

    @Test
    void reportsSafeActionableTransportErrorsWithoutLeakingUpstreamDetails() throws Exception {
        for (var failure : List.of(new HttpConnectTimeoutException("test-secret-key"),
                new HttpTimeoutException("test-secret-key"), new UnknownHostException("test-secret-key"),
                new ConnectException("test-secret-key"), new SSLHandshakeException("test-secret-key"))) {
            var isolated = new GameAiConnectionService(new ObjectMapper(), credentials, games, rooms, client);
            when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenThrow(failure);
            var result = isolated.test(player, own);
            assertFalse(result.connected());
            assertFalse(result.message().contains("test-secret-key"));
            assertFalse(result.message().startsWith("API Key 无效"));
            assertFalse(result.message().startsWith("连接失败"));
        }
    }

    @Test
    void onlyTheRoomHostCanProbeItsStoredCredential() {
        var room = mock(EntertainmentDtos.RoomResponse.class);
        when(rooms.get(5L, player)).thenReturn(room);
        when(room.host()).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.test(player,
                new AiConnectionDtos.Request("ROOM", null, 5L, null)));
        verifyNoInteractions(client, credentials);
    }

    @Test
    @SuppressWarnings("unchecked")
    void oversizedUpstreamBodiesAreCancelled() throws Exception {
        var method = GameAiConnectionService.class.getDeclaredMethod("boundedResponse");
        method.setAccessible(true);
        var subscriber = (HttpResponse.BodySubscriber<String>) method.invoke(null);
        var subscription = mock(Flow.Subscription.class);
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.allocate(65537)));
        assertThrows(Exception.class, () -> subscriber.getBody().toCompletableFuture().join());
        verify(subscription).cancel();
    }
}
