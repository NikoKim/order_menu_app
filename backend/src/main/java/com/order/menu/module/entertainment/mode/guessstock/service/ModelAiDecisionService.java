package com.order.menu.module.entertainment.mode.guessstock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.menu.module.entertainment.core.service.GameAiCredentialService;
import com.order.menu.module.entertainment.mode.guessstock.agent.AiInvalidCondition;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class ModelAiDecisionService {
    private static final String SYSTEM_PROMPT = """
            你是虚拟股票博弈的自主投资 Agent。你只维护可证伪的投资观点并决定目标总仓位；服务器负责事实校验、订单方向、股数和风控。延续有效观点优先 KEEP，只有新证据才调整。禁止输出 BUY、SELL、HOLD、股数、做空或杠杆。只返回指定 JSON。
            """;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, Boolean> jsonModeCapabilities = new ConcurrentHashMap<>();
    private final ThreadLocal<InvocationTelemetry> lastTelemetry = new ThreadLocal<>();

    public ModelAiDecisionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(1500)).build();
    }

    public Decision decide(GameAiCredentialService.DecryptedCredential credential, Context context, String modelRoute) {
        long startedAt = System.nanoTime();
        int attempts = 0;
        int httpStatus = 0;
        int promptTokens = 0;
        int completionTokens = 0;
        try {
            String prompt = buildPrompt(context);
            double temperature = "STRONG".equals(modelRoute) ? .04 : switch (context.difficulty()) {
                case "HARD" -> .06;
                case "EASY" -> .14;
                default -> .10;
            };
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", credential.model());
            body.put("temperature", temperature);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user", "content", prompt)));
            String capabilityKey = credential.baseUrl() + "|" + credential.model();
            if (!Boolean.FALSE.equals(jsonModeCapabilities.get(capabilityKey))) {
                body.put("response_format", Map.of("type", "json_object"));
            }
            HttpResponse<String> response = send(credential, body, modelRoute);
            attempts++;
            httpStatus = response.statusCode();
            if (response.statusCode() == 400 && body.containsKey("response_format")) {
                body.remove("response_format");
                jsonModeCapabilities.put(capabilityKey, false);
                response = send(credential, body, modelRoute);
                attempts++;
                httpStatus = response.statusCode();
            } else if (response.statusCode() >= 200 && response.statusCode() < 300
                    && body.containsKey("response_format")) {
                jsonModeCapabilities.put(capabilityKey, true);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ModelDecisionException("模型接口返回 HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            promptTokens = root.path("usage").path("prompt_tokens").asInt(0);
            completionTokens = root.path("usage").path("completion_tokens").asInt(0);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            return parse(content);
        } catch (ModelDecisionException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ModelDecisionException("模型请求失败或响应格式错误");
        } finally {
            lastTelemetry.set(new InvocationTelemetry(
                    Math.max(0, (System.nanoTime() - startedAt) / 1_000_000),
                    promptTokens, completionTokens, httpStatus, Math.max(1, attempts)));
        }
    }

    public InvocationTelemetry consumeLastTelemetry() {
        InvocationTelemetry telemetry = lastTelemetry.get();
        lastTelemetry.remove();
        return telemetry;
    }

    private HttpResponse<String> send(GameAiCredentialService.DecryptedCredential credential,
                                      Map<String, Object> body, String modelRoute) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(credential.baseUrl() + "/chat/completions"))
                .timeout(Duration.ofMillis("STRONG".equals(modelRoute) ? 5000 : 3000))
                .header("Authorization", "Bearer " + credential.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    String buildPrompt(Context context) {
        try {
            Map<String, Object> packet = new LinkedHashMap<>();
            packet.put("round", context.roundNo());
            packet.put("left", Math.max(0, context.totalRounds() - context.roundNo()));
            packet.put("phase", context.stage());
            packet.put("profile", Map.of("style", context.style(), "difficulty", context.difficulty(),
                    "philosophy", styleGuidance(context.style()), "custom", nullSafe(context.strategyPrompt())));
            packet.put("market", Map.of(
                    "price", decimal(context.referencePrice()), "closes", context.recentCloses(),
                    "ret", Map.of("r3", round2(context.return3()), "r8", round2(context.return8()), "r20", round2(context.return20())),
                    "slope", Map.of("r3", round2(context.slope3()), "r8", round2(context.slope8()), "r20", round2(context.slope20())),
                    "vol", Map.of("short", round2(context.volatility8Percent()), "long", round2(context.volatility20Percent())),
                    "range", List.of(decimal(context.recentLow()), decimal(context.recentHigh())),
                    "flow", round2(context.orderImbalancePercent()), "regime", context.marketRegime(),
                    "previousRegime", context.previousMarketRegime(), "regimeChanged", context.regimeChanged()));
            packet.put("drawdown", Map.of("fromHigh", round2(context.drawdownFromHighPct()),
                    "max", round2(context.maxDrawdownPct())));
            packet.put("state", Map.of("trend", context.trendState(), "volatility", context.volatilityState(),
                    "trendAge", context.trendAge(), "regimeAge", context.regimeAge(),
                    "consecutive", context.consecutiveDirectionRounds()));
            packet.put("eventDigest", Map.of("residualSignal", nullSafe(context.eventResidualSignal()),
                    "remainingStages", Math.max(0, context.visibleRemainingEventStages())));
            packet.put("event", Map.of("name", nullSafe(context.eventName()), "description", limited(nullSafe(context.eventDescription()), 180),
                    "type", nullSafe(context.eventType()), "direction", nullSafe(context.eventDirection()),
                    "strength", nullSafe(context.eventStrength()), "isNew", context.eventNew()));
            packet.put("portfolio", Map.of("exposure", round2(context.currentPositionPercent()), "cash", decimal(context.cash()),
                    "shares", context.quantity(), "cost", decimal(context.averageCost()), "rank", context.assetRank(),
                    "participants", context.participantCount(), "pending", nullSafe(context.pendingOrder())));
            packet.put("credit", Map.of("product", nullSafe(context.creditProduct()), "debt", decimal(context.creditDebt()),
                    "interest", decimal(context.creditInterest()), "ltv", round2(context.creditLtvPercent()),
                    "risk", nullSafe(context.creditRisk()), "available", decimal(context.availableCredit()),
                    "maturityRound", context.creditMaturityRound()));
            packet.put("thesis", jsonValue(context.previousThesis()));
            packet.put("evidence", jsonValue(context.newEvidence()));
            packet.put("triggered", jsonValue(context.triggeredInvalidConditions()));
            packet.put("tradeStats", Map.of("buys", context.recentBuys(), "losingBuys", context.recentLosingBuys(),
                    "sells", context.recentSells(), "roundTurnover", round2(context.roundTurnoverPct())));
            packet.put("limits", Map.of("maxExposure", decimal(context.maxPositionPercent()),
                    "rebalanceBuffer", decimal(context.rebalanceBandPctPoints()), "minHold", context.minHoldRounds(),
                    "minTradePct", decimal(context.minTradeAssetPct()), "maxTurnover", decimal(context.maxRoundTurnoverPct()),
                    "buyImpact", round2(context.estimatedBuyImpactPct()), "sellImpact", round2(context.estimatedSellImpactPct())));
            return """
                    根据 DecisionPacket 延续或修订投资观点。服务端 regime 仅是客观走势分类，不代表买卖建议。target_exposure_pct 表示股票市值/净资产，100以上需要银行融资；银行选择、借款金额、LTV和强平由后端处理。阶段暂时 OPENING 只执行加仓、CLOSING 只执行减仓，不能执行的目标会保留。最终股票按收盘价折算。
                    KEEP 必须保持目标、方向、期限和失效条件；ADJUST 只调整同方向观点；方向变化用 REPLACE_THESIS；无观点用 CREATE_THESIS；退出用 CLOSE_THESIS。
                    invalid_conditions 只允许 PRICE_BELOW、PRICE_ABOVE、RETURN_BELOW、RETURN_ABOVE、DRAWDOWN_ABOVE、VOLATILITY_ABOVE、ORDER_IMBALANCE_BUY_ABOVE、ORDER_IMBALANCE_SELL_ABOVE、EVENT_DIRECTION、EVENT_TYPE、THESIS_AGE_ABOVE、TREND_STATE_CHANGED、VOLATILITY_STATE_ABOVE、REGIME_AGE_ABOVE，且必须包含可验证参数。
                    DecisionPacket=%s
                    只返回：{"decision_type":"CREATE_THESIS|KEEP|ADJUST|REPLACE_THESIS|CLOSE_THESIS","target_exposure_pct":0到%s,"confidence":0到1,"urgency":"LOW|MEDIUM|HIGH","market_regime":"简短状态","direction":"BULLISH|BEARISH|NEUTRAL","time_horizon":"SHORT|MEDIUM|LONG","research_action":"NONE|VERIFY_EVENT|SURVEY_DEMAND|PREVIEW_REPORT|CHECK_LIQUIDITY","reason_codes":["最多3项"],"invalid_conditions":[{"type":"PRICE_BELOW","value":28.8}],"review_after_rounds":1到20,"expire_after_rounds":2到50,"reason":"不超过60字"}
                    """.formatted(objectMapper.writeValueAsString(packet), context.maxPositionPercent());
        } catch (Exception exception) {
            throw new ModelDecisionException("模型决策包生成失败");
        }
    }

    private Object jsonValue(String value) {
        if (value == null || value.isBlank()) return null;
        try { return objectMapper.readTree(value); }
        catch (Exception ignored) { return limited(value, 200); }
    }

    private static BigDecimal decimal(String value) {
        try { return new BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP); }
        catch (Exception ignored) { return BigDecimal.ZERO.setScale(2); }
    }

    private static double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }

    private static String nullSafe(String value) { return value == null ? "" : value; }

    String styleGuidance(String style) {
        return switch (style == null ? "" : style.toUpperCase()) {
            case "CONSERVATIVE", "稳健" -> "你重视本金、回撤和现金余量，偏好证据充分后逐步建仓，并主动定义较严格的观点失效条件。";
            case "TREND", "趋势" -> "你不预测最低点，重视多周期方向和趋势延续性；趋势形成后参与，趋势逻辑失效后退出。";
            case "CONTRARIAN", "逆势" -> "你寻找市场过度反应和均值回归机会，但价格便宜本身不是理由，需要自行判断抛压、支撑和事件定价。";
            case "AGGRESSIVE", "激进" -> "你愿意为高置信机会承担较大波动并快速调整股票敞口，但必须说明风险收益依据和判断失效条件。";
            case "RANDOM", "灵活", "随机" -> "你可在趋势、估值、事件和订单流方法间切换，根据证据强弱形成连贯观点，而不是随机交易。";
            default -> "根据公开事实形成独立、连续且可证伪的投资观点，并用目标股票敞口表达判断强度。";
        };
    }

    Decision parse(String content) {
        try {
            String json = content == null ? "" : content.trim();
            if (json.startsWith("```")) {
                int firstLine = json.indexOf('\n');
                int closing = json.lastIndexOf("```");
                json = firstLine >= 0 && closing > firstLine ? json.substring(firstLine + 1, closing).trim() : json;
            }
            JsonNode node = objectMapper.readTree(json);
            String decisionType = textValue(node, "decision_type", "decisionType").toUpperCase();
            if (!List.of("CREATE_THESIS", "KEEP", "ADJUST", "REPLACE_THESIS", "CLOSE_THESIS").contains(decisionType)) {
                throw new ModelDecisionException("模型返回了不支持的观点决策类型");
            }
            JsonNode targetNode = node.has("target_exposure_pct") ? node.get("target_exposure_pct")
                    : node.has("targetExposure") ? node.get("targetExposure")
                    : node.has("target_position_pct") ? node.get("target_position_pct") : node.get("targetPositionPct");
            if (targetNode == null || !targetNode.isNumber()) {
                throw new ModelDecisionException("模型没有返回有效目标敞口");
            }
            double targetPosition = Math.max(0, Math.min(300, targetNode.asDouble()));
            double confidence = Math.max(0, Math.min(1, node.path("confidence").asDouble(.5)));
            String marketRegime = limited(node.path("market_regime").asText("未分类"), 40);
            String direction = node.path("direction").asText("NEUTRAL").toUpperCase();
            if (!List.of("BULLISH", "BEARISH", "NEUTRAL").contains(direction)) direction = "NEUTRAL";
            String timeHorizon = node.path("time_horizon").asText("MEDIUM").toUpperCase();
            if (!List.of("SHORT", "MEDIUM", "LONG").contains(timeHorizon)) timeHorizon = "MEDIUM";
            String urgency = node.path("urgency").asText("MEDIUM").toUpperCase();
            if (!List.of("LOW", "MEDIUM", "HIGH").contains(urgency)) urgency = "MEDIUM";
            String researchAction = node.path("research_action").asText("NONE").toUpperCase();
            if (!List.of("NONE", "VERIFY_EVENT", "SURVEY_DEMAND", "PREVIEW_REPORT", "CHECK_LIQUIDITY")
                    .contains(researchAction)) researchAction = "NONE";
            List<String> reasonCodes = stringList(node.path("reason_codes"), 3, 50);
            List<AiInvalidCondition> invalidConditions = conditionList(node.path("invalid_conditions"));
            int reviewAfterRounds = boundedInt(node, "review_after_rounds", 2, 1, 20);
            int expireAfterRounds = boundedInt(node, "expire_after_rounds", 6, 2, 50);
            if (expireAfterRounds < reviewAfterRounds) {
                throw new ModelDecisionException("观点有效期不能短于复核周期");
            }
            String reason = node.path("reason").asText("模型未提供理由").trim();
            if (reason.length() > 60) reason = reason.substring(0, 60);
            return new Decision(decisionType, targetPosition, confidence, urgency, marketRegime, direction,
                    timeHorizon, reasonCodes, invalidConditions, reviewAfterRounds, expireAfterRounds, reason,
                    researchAction);
        } catch (Exception exception) {
            if (exception instanceof ModelDecisionException modelException) throw modelException;
            throw new ModelDecisionException("模型没有返回有效 JSON");
        }
    }

    private static List<String> stringList(JsonNode node, int maxItems, int maxLength) {
        if (!node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = limited(item.asText(""), maxLength);
            if (!value.isBlank()) values.add(value);
            if (values.size() >= maxItems) break;
        }
        return List.copyOf(values);
    }

    private static List<AiInvalidCondition> conditionList(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> allowed = List.of("PRICE_BELOW", "PRICE_ABOVE", "RETURN_BELOW", "RETURN_ABOVE",
                "DRAWDOWN_ABOVE", "VOLATILITY_ABOVE", "ORDER_IMBALANCE_BUY_ABOVE",
                "ORDER_IMBALANCE_SELL_ABOVE", "EVENT_DIRECTION", "EVENT_TYPE", "THESIS_AGE_ABOVE",
                "TREND_STATE_CHANGED", "VOLATILITY_STATE_ABOVE", "REGIME_AGE_ABOVE");
        List<AiInvalidCondition> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isObject()) throw new ModelDecisionException("观点失效条件必须是结构化对象");
            String type = item.path("type").asText("").toUpperCase();
            if (!allowed.contains(type)) throw new ModelDecisionException("观点失效条件包含未知类型");
            AiInvalidCondition condition = new AiInvalidCondition(type, decimal(item, "value"), integer(item, "windowRounds", "window_rounds"),
                    decimal(item, "valuePct", "value_pct"), limited(item.path("direction").asText(null), 20),
                    limited(item.path("severity").asText(null), 20), limited(item.path("eventType").asText(
                            item.path("event_type").asText(null)), 30));
            validateCondition(condition);
            values.add(condition);
            if (values.size() >= 5) break;
        }
        return List.copyOf(values);
    }

    private static void validateCondition(AiInvalidCondition condition) {
        String type = condition.type();
        java.math.BigDecimal threshold = condition.value() != null ? condition.value() : condition.valuePct();
        if (List.of("PRICE_BELOW", "PRICE_ABOVE", "RETURN_BELOW", "RETURN_ABOVE", "DRAWDOWN_ABOVE",
                "VOLATILITY_ABOVE", "ORDER_IMBALANCE_BUY_ABOVE", "ORDER_IMBALANCE_SELL_ABOVE",
                "THESIS_AGE_ABOVE", "REGIME_AGE_ABOVE", "EVENT_RESIDUAL_BELOW").contains(type) && threshold == null) {
            throw new ModelDecisionException(type + " 缺少可验证的阈值");
        }
        if (condition.windowRounds() != null
                && (condition.windowRounds() < 1 || condition.windowRounds() > 20)) {
            throw new ModelDecisionException("观点失效条件的统计周期需在 1 到 20 回合之间");
        }
        if (List.of("DRAWDOWN_ABOVE", "VOLATILITY_ABOVE", "ORDER_IMBALANCE_BUY_ABOVE",
                "ORDER_IMBALANCE_SELL_ABOVE", "THESIS_AGE_ABOVE", "REGIME_AGE_ABOVE",
                "EVENT_RESIDUAL_BELOW").contains(type)
                && threshold.signum() < 0) {
            throw new ModelDecisionException(type + " 的阈值不能为负数");
        }
        if ("EVENT_DIRECTION".equals(type)) {
            String direction = condition.direction() == null ? "" : condition.direction().toUpperCase();
            if (!List.of("POSITIVE", "NEGATIVE", "UP", "DOWN").contains(direction)) {
                throw new ModelDecisionException("EVENT_DIRECTION 缺少有效方向");
            }
        }
        if ("EVENT_TYPE".equals(type) && (condition.eventType() == null || condition.eventType().isBlank())) {
            throw new ModelDecisionException("EVENT_TYPE 缺少事件类型");
        }
        if ("TREND_STATE_CHANGED".equals(type)
                && (condition.direction() == null || !List.of("STRONG_UP", "UP", "SIDEWAYS", "DOWN", "STRONG_DOWN")
                    .contains(condition.direction().toUpperCase()))) {
            throw new ModelDecisionException("TREND_STATE_CHANGED 缺少原趋势状态");
        }
        if ("VOLATILITY_STATE_ABOVE".equals(type)
                && (condition.severity() == null || !List.of("LOW", "NORMAL", "HIGH", "EXTREME")
                    .contains(condition.severity().toUpperCase()))) {
            throw new ModelDecisionException("VOLATILITY_STATE_ABOVE 缺少波动状态");
        }
    }

    private static java.math.BigDecimal decimal(JsonNode node, String... names) {
        for (String name : names) if (node.has(name) && node.get(name).isNumber()) return node.get(name).decimalValue();
        return null;
    }

    private static Integer integer(JsonNode node, String... names) {
        for (String name : names) if (node.has(name) && node.get(name).canConvertToInt()) return node.get(name).asInt();
        return null;
    }

    private static int boundedInt(JsonNode node, String name, int fallback, int min, int max) {
        int value = node.path(name).asInt(fallback);
        return Math.max(min, Math.min(max, value));
    }

    private static String textValue(JsonNode node, String... names) {
        for (String name : names) if (node.has(name)) return node.path(name).asText("");
        return "";
    }

    private static String limited(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    public record Context(String style, String difficulty, int roundNo, int totalRounds, String stage, String referencePrice,
                          String averageCost, String cash, long quantity, List<BigDecimal> recentCloses,
                          long marketBuyQuantity, long marketSellQuantity, int assetRank, int participantCount,
                          String eventName, String eventDescription, String eventType, String eventDirection,
                          String eventStrength, boolean eventNew, String initialCash, String activityPercent,
                          String maxPositionPercent, String strategyPrompt, double currentPositionPercent,
                          double return3, double return8, double return20, double volatility8Percent,
                          double volatility20Percent, double slope3, double slope8, double slope20,
                          double trendAcceleration3, double orderImbalancePercent,
                          String recentHigh, String recentLow, String marketRegime, String previousMarketRegime,
                          boolean regimeChanged, int recentBuys, int recentLosingBuys, int recentSells,
                          String pendingOrder, String previousThesis, String triggeredInvalidConditions,
                          String newEvidence, String rebalanceBandPctPoints, int minHoldRounds,
                          String minTradeAssetPct, String maxRoundTurnoverPct, double roundTurnoverPct,
                          double estimatedBuyImpactPct, double estimatedSellImpactPct,
                          double drawdownFromHighPct, double maxDrawdownPct, String trendState,
                          String volatilityState, int trendAge, int regimeAge, int consecutiveDirectionRounds,
                          String eventResidualSignal, int visibleRemainingEventStages,
                          boolean revision, String creditProduct, String creditDebt, String creditInterest,
                          double creditLtvPercent, String creditRisk, String availableCredit,
                          int creditMaturityRound) {}
    public record Decision(String decisionType, double targetPositionPct, double confidence, String urgency,
                           String marketRegime, String direction, String timeHorizon, List<String> reasonCodes,
                           List<AiInvalidCondition> invalidConditions, int reviewAfterRounds,
                           int expireAfterRounds, String reason, String researchAction) {
        public Decision(String decisionType, double targetPositionPct, double confidence, String urgency,
                        String marketRegime, String direction, String timeHorizon, List<String> reasonCodes,
                        List<AiInvalidCondition> invalidConditions, int reviewAfterRounds,
                        int expireAfterRounds, String reason) {
            this(decisionType, targetPositionPct, confidence, urgency, marketRegime, direction, timeHorizon,
                    reasonCodes, invalidConditions, reviewAfterRounds, expireAfterRounds, reason, "NONE");
        }
    }
    public record InvocationTelemetry(long latencyMs, int promptTokens, int completionTokens,
                                      int httpStatus, int attemptCount) {}

    public static class ModelDecisionException extends RuntimeException {
        public ModelDecisionException(String message) { super(message); }
    }
}
