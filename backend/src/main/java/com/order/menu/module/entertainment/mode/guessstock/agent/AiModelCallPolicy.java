package com.order.menu.module.entertainment.mode.guessstock.agent;

public final class AiModelCallPolicy {
    private AiModelCallPolicy() {}

    public static boolean shouldCall(AiStrategyPlanner.State previous, AiMarketSnapshot market,
                                     boolean invalidated, boolean newReport, boolean newResearch,
                                     AiModelSettings settings) {
        if (previous == null || previous.lastModelRound() <= 0) return true;
        int elapsed = market.roundNo() - previous.lastModelRound();
        if (elapsed < settings.modelGap()) return false;
        double shock = Math.abs(market.priceDeviationPct()) / Math.max(.5, market.baseVolatilityPct());
        boolean majorInformation = (newReport || newResearch) && (shock >= 1 || market.regimeChanged());
        return elapsed >= settings.reviewInterval() || invalidated || majorInformation
                || market.criticalEvent() || market.regimeChanged() && shock >= 2 || shock >= 3;
    }
}
