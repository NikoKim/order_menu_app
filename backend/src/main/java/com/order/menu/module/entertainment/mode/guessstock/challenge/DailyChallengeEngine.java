package com.order.menu.module.entertainment.mode.guessstock.challenge;

import java.time.LocalDate;

/** Stable daily scenario identity and transparent leaderboard metrics. */
public final class DailyChallengeEngine {
    private DailyChallengeEngine() {}

    public static Challenge create(LocalDate date, long versionId, String rulesVersion) {
        String key = date + ":" + versionId + ":" + (rulesVersion == null ? "V1" : rulesVersion);
        long hash = 0xcbf29ce484222325L;
        for (int index = 0; index < key.length(); index++) { hash ^= key.charAt(index); hash *= 0x100000001b3L; }
        return new Challenge(date, versionId, rulesVersion, mix(hash), key);
    }

    public static Score score(double returnPct, double maxDrawdownPct, int liquidations,
                              double interestCost, int accurateResearch) {
        return new Score(returnPct, maxDrawdownPct, liquidations, interestCost, accurateResearch,
                returnPct > 0 && maxDrawdownPct >= -5,
                liquidations == 0, interestCost == 0);
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
    public record Challenge(LocalDate challengeDate, long gameVersionId, String rulesVersion,
                            long scenarioSeed, String immutableKey) {}
    public record Score(double returnPct, double maxDrawdownPct, int liquidations,
                        double interestCost, int accurateResearch, boolean lowDrawdownBadge,
                        boolean noLiquidationBadge, boolean noDebtCostBadge) {}
}
