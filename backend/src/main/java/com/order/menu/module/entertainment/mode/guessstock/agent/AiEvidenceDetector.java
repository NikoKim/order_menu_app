package com.order.menu.module.entertainment.mode.guessstock.agent;

import com.order.menu.module.entertainment.mode.guessstock.entity.AiInvestmentThesis;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiEvidenceDetector {
    public EvidenceResult detect(AiInvestmentThesis thesis, AiMarketSnapshot snapshot,
                                 AiThesisConditionEvaluator.ValidationResult validation) {
        List<Evidence> values = new ArrayList<>();
        if (validation.invalidated()) values.add(new Evidence("INVALID_CONDITION_TRIGGERED", "HIGH",
                validation.triggeredConditions().size()));
        if (snapshot.hasPublicEvent() && snapshot.stage().equals(snapshot.eventPublishedStage())) {
            values.add(new Evidence("NEW_PUBLIC_EVENT", snapshot.criticalEvent() ? "CRITICAL" : eventImportance(snapshot), 1));
        }
        double deviationThreshold = Math.max(.5, snapshot.baseVolatilityPct());
        if (Math.abs(snapshot.priceDeviationPct()) >= deviationThreshold) {
            values.add(new Evidence("PRICE_DEVIATION", Math.abs(snapshot.priceDeviationPct()) >= deviationThreshold * 2
                    ? "HIGH" : "MEDIUM", snapshot.priceDeviationPct()));
        }
        if (Math.abs(snapshot.orderImbalancePct()) >= 20) {
            values.add(new Evidence("ORDER_IMBALANCE", Math.abs(snapshot.orderImbalancePct()) >= 50 ? "HIGH" : "MEDIUM",
                    snapshot.orderImbalancePct()));
        }
        if (snapshot.volatility8Pct() > Math.max(1, snapshot.baseVolatilityPct() * 2)) {
            values.add(new Evidence("ABNORMAL_VOLATILITY", "HIGH", snapshot.volatility8Pct()));
        }
        if (snapshot.regimeChanged()) {
            values.add(new Evidence("MARKET_REGIME_CHANGED",
                    snapshot.marketRegime().startsWith("STRONG_") || "HIGH_VOLATILITY".equals(snapshot.marketRegime())
                            ? "HIGH" : "MEDIUM", 1));
        }
        double drawdown = Math.abs(snapshot.drawdownFromHighPct());
        if (drawdownBand(drawdown, snapshot.baseVolatilityPct())
                > drawdownBand(previousDrawdown(snapshot), snapshot.baseVolatilityPct())) {
            values.add(new Evidence("DRAWDOWN_BAND_CROSSED",
                    drawdown >= Math.max(8, snapshot.baseVolatilityPct() * 4) ? "HIGH" : "MEDIUM", drawdown));
        }
        if (breaksPreviousRange(snapshot)) {
            values.add(new Evidence("PRICE_RANGE_BREAK", "MEDIUM", snapshot.currentPrice().doubleValue()));
        }
        if (thesis != null) {
            int sinceValidation = snapshot.roundNo() - thesis.getLastValidatedRound();
            int age = snapshot.roundNo() - (thesis.getRenewedRound() == null
                    ? thesis.getCreatedRound() : thesis.getRenewedRound());
            if (sinceValidation >= thesis.getReviewAfterRounds()) values.add(new Evidence("THESIS_REVIEW_DUE", "MEDIUM", sinceValidation));
            if (age >= thesis.getExpireAfterRounds()) values.add(new Evidence("THESIS_EXPIRED", "HIGH", age));
        }
        boolean critical = snapshot.criticalEvent() || values.stream().anyMatch(item -> "CRITICAL".equals(item.importance()));
        return new EvidenceResult(!values.isEmpty(), critical, List.copyOf(values));
    }

    private static boolean breaksPreviousRange(AiMarketSnapshot snapshot) {
        List<BigDecimal> prices = snapshot.recentPrices();
        if (prices.size() < 3) return false;
        List<BigDecimal> prior = prices.subList(0, prices.size() - 1);
        BigDecimal priorHigh = prior.stream().max(BigDecimal::compareTo).orElse(snapshot.currentPrice());
        BigDecimal priorLow = prior.stream().min(BigDecimal::compareTo).orElse(snapshot.currentPrice());
        return snapshot.currentPrice().compareTo(priorHigh) > 0 || snapshot.currentPrice().compareTo(priorLow) < 0;
    }

    private static int drawdownBand(double drawdown, double baseVolatilityPct) {
        double medium = Math.max(3, baseVolatilityPct * 2);
        double high = Math.max(8, baseVolatilityPct * 4);
        if (drawdown >= high) return 2;
        return drawdown >= medium ? 1 : 0;
    }

    private static double previousDrawdown(AiMarketSnapshot snapshot) {
        List<BigDecimal> prices = snapshot.recentPrices();
        if (prices.size() < 2) return 0;
        double high = 0;
        double maximum = 0;
        for (int index = 0; index < prices.size() - 1; index++) {
            double price = prices.get(index).doubleValue();
            high = Math.max(high, price);
            if (high > 0) maximum = Math.min(maximum, (price / high - 1) * 100);
        }
        return Math.abs(maximum);
    }

    private static String eventImportance(AiMarketSnapshot snapshot) {
        if ("STRONG".equalsIgnoreCase(snapshot.eventStrength()) || "HIGH".equalsIgnoreCase(snapshot.eventRelevance())) return "HIGH";
        if ("MEDIUM".equalsIgnoreCase(snapshot.eventStrength())) return "MEDIUM";
        return "LOW";
    }

    public record Evidence(String type, String importance, double value) {}
    public record EvidenceResult(boolean materialChange, boolean criticalRisk, List<Evidence> evidences) {}
}
