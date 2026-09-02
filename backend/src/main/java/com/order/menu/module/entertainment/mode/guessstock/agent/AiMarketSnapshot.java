package com.order.menu.module.entertainment.mode.guessstock.agent;

import java.math.BigDecimal;
import java.util.List;

public record AiMarketSnapshot(
        int roundNo,
        int remainingRounds,
        String stage,
        BigDecimal currentPrice,
        BigDecimal previousClosePrice,
        List<BigDecimal> recentPrices,
        double return3Pct,
        double return8Pct,
        double return20Pct,
        double volatility8Pct,
        double volatility20Pct,
        double slope3Pct,
        double slope8Pct,
        double slope20Pct,
        double trendAcceleration3Pct,
        BigDecimal recentHigh,
        BigDecimal recentLow,
        long publicBuyQuantity,
        long publicSellQuantity,
        double orderImbalancePct,
        double priceDeviationPct,
        double baseVolatilityPct,
        String eventName,
        String eventDescription,
        String eventDirection,
        String eventType,
        String eventRelevance,
        String eventCredibility,
        String eventSurprise,
        String eventStrength,
        int eventPublishedRound,
        String eventPublishedStage,
        String marketRegime,
        String previousMarketRegime,
        boolean regimeChanged,
        double drawdownFromHighPct,
        double maxDrawdownPct,
        String trendState,
        String volatilityState,
        int trendAge,
        int regimeAge,
        int consecutiveDirectionRounds,
        PublicEventDigest publicEventDigest
) implements PublicDecisionSnapshot {
    public double returnPct(int windowRounds) {
        if (windowRounds <= 3) return return3Pct;
        if (windowRounds <= 8) return return8Pct;
        return return20Pct;
    }

    public double volatilityPct(int windowRounds) {
        return windowRounds <= 8 ? volatility8Pct : volatility20Pct;
    }

    public boolean hasPublicEvent() {
        return eventName != null && !eventName.isBlank() && !"无事件".equals(eventName);
    }

    public boolean criticalEvent() {
        return hasPublicEvent() && "STRONG".equalsIgnoreCase(eventStrength)
                && "CONFIRMED".equalsIgnoreCase(eventCredibility)
                && "MAJOR".equalsIgnoreCase(eventSurprise);
    }

    public List<BigDecimal> recentCloses() {
        int from = Math.max(0, recentPrices.size() - 4);
        return recentPrices.subList(from, recentPrices.size());
    }
}
