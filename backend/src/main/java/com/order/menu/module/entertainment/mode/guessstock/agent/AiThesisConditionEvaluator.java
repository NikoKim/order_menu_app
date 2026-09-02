package com.order.menu.module.entertainment.mode.guessstock.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.menu.module.entertainment.mode.guessstock.entity.AiInvestmentThesis;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiThesisConditionEvaluator {
    private final ObjectMapper objectMapper;

    public AiThesisConditionEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ValidationResult evaluate(AiInvestmentThesis thesis, AiMarketSnapshot snapshot) {
        if (thesis == null || thesis.getInvalidConditionsJson() == null) return new ValidationResult(false, List.of());
        List<AiInvalidCondition> conditions;
        try {
            conditions = objectMapper.readValue(thesis.getInvalidConditionsJson(), new TypeReference<>() {});
        } catch (Exception ignored) {
            return new ValidationResult(false, List.of());
        }
        List<TriggeredCondition> triggered = new ArrayList<>();
        for (AiInvalidCondition condition : conditions) {
            TriggeredCondition result = evaluateOne(condition, thesis, snapshot);
            if (result != null) triggered.add(result);
        }
        return new ValidationResult(!triggered.isEmpty(), List.copyOf(triggered));
    }

    private TriggeredCondition evaluateOne(AiInvalidCondition condition, AiInvestmentThesis thesis,
                                            AiMarketSnapshot snapshot) {
        String type = condition.type() == null ? "" : condition.type().toUpperCase();
        BigDecimal threshold = condition.value() != null ? condition.value() : condition.valuePct();
        double actual;
        boolean hit;
        switch (type) {
            case "PRICE_BELOW" -> {
                if (threshold == null) return null;
                actual = snapshot.currentPrice().doubleValue();
                hit = snapshot.currentPrice().compareTo(threshold) < 0;
            }
            case "PRICE_ABOVE" -> {
                if (threshold == null) return null;
                actual = snapshot.currentPrice().doubleValue();
                hit = snapshot.currentPrice().compareTo(threshold) > 0;
            }
            case "RETURN_BELOW", "RETURN_ABOVE" -> {
                if (threshold == null) return null;
                actual = snapshot.returnPct(condition.windowRounds() == null ? 3 : condition.windowRounds());
                hit = "RETURN_BELOW".equals(type) ? actual < threshold.doubleValue() : actual > threshold.doubleValue();
            }
            case "DRAWDOWN_ABOVE" -> {
                if (threshold == null || snapshot.recentHigh().signum() == 0) return null;
                actual = snapshot.recentHigh().subtract(snapshot.currentPrice())
                        .divide(snapshot.recentHigh(), 10, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();
                hit = actual > threshold.doubleValue();
            }
            case "VOLATILITY_ABOVE" -> {
                if (threshold == null) return null;
                actual = snapshot.volatilityPct(condition.windowRounds() == null ? 8 : condition.windowRounds());
                hit = actual > threshold.doubleValue();
            }
            case "ORDER_IMBALANCE_BUY_ABOVE" -> {
                if (threshold == null) return null;
                actual = snapshot.orderImbalancePct();
                hit = actual > threshold.doubleValue();
            }
            case "ORDER_IMBALANCE_SELL_ABOVE" -> {
                if (threshold == null) return null;
                actual = -snapshot.orderImbalancePct();
                hit = actual > threshold.doubleValue();
            }
            case "EVENT_DIRECTION" -> {
                if (condition.direction() == null || !snapshot.hasPublicEvent()) return null;
                actual = severityRank(snapshot.eventStrength());
                int required = severityRank(condition.severity());
                hit = directionMatches(condition.direction(), snapshot.eventDirection()) && actual >= required;
                threshold = BigDecimal.valueOf(required);
            }
            case "EVENT_TYPE" -> {
                if (condition.eventType() == null || !snapshot.hasPublicEvent()) return null;
                actual = condition.eventType().equalsIgnoreCase(snapshot.eventType()) ? 1 : 0;
                hit = actual == 1;
                threshold = BigDecimal.ONE;
            }
            case "THESIS_AGE_ABOVE" -> {
                if (threshold == null) return null;
                actual = snapshot.roundNo() - thesis.getCreatedRound();
                hit = actual > threshold.doubleValue();
            }
            case "TREND_STATE_CHANGED" -> {
                if (condition.direction() == null) return null;
                actual = condition.direction().equalsIgnoreCase(snapshot.trendState()) ? 0 : 1;
                hit = actual == 1;
                threshold = BigDecimal.ONE;
            }
            case "VOLATILITY_STATE_ABOVE" -> {
                if (condition.severity() == null) return null;
                actual = volatilityRank(snapshot.volatilityState());
                int required = volatilityRank(condition.severity());
                hit = actual > required;
                threshold = BigDecimal.valueOf(required);
            }
            case "REGIME_AGE_ABOVE" -> {
                if (threshold == null) return null;
                actual = snapshot.regimeAge();
                hit = actual > threshold.doubleValue();
            }
            case "EVENT_RESIDUAL_BELOW" -> {
                if (threshold == null) return null;
                // Old theses remain readable, but are evaluated against public tiers only.
                actual = switch (snapshot.publicEventDigest().residualSignal()) {
                    case NONE -> 0;
                    case LOW -> 1;
                    case MEDIUM -> 2;
                    case HIGH -> 3;
                };
                hit = actual < threshold.doubleValue();
            }
            default -> { return null; }
        }
        return hit ? new TriggeredCondition(type, threshold, BigDecimal.valueOf(actual)) : null;
    }

    private static boolean directionMatches(String expected, String actual) {
        if (actual == null) return false;
        String normalized = expected.toUpperCase();
        return normalized.equals(actual.toUpperCase())
                || ("NEGATIVE".equals(normalized) && "DOWN".equalsIgnoreCase(actual))
                || ("POSITIVE".equals(normalized) && "UP".equalsIgnoreCase(actual));
    }

    private static int severityRank(String value) {
        return switch (value == null ? "" : value.toUpperCase()) {
            case "STRONG", "HIGH", "CRITICAL" -> 3;
            case "MEDIUM", "NORMAL" -> 2;
            default -> 1;
        };
    }

    private static int volatilityRank(String value) {
        return switch (value == null ? "" : value.toUpperCase()) {
            case "EXTREME" -> 4;
            case "HIGH" -> 3;
            case "NORMAL" -> 2;
            default -> 1;
        };
    }

    public record TriggeredCondition(String type, BigDecimal threshold, BigDecimal actual) {}
    public record ValidationResult(boolean invalidated, List<TriggeredCondition> triggeredConditions) {}
}
