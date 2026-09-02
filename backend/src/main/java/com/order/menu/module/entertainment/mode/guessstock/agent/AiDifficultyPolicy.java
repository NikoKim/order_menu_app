package com.order.menu.module.entertainment.mode.guessstock.agent;

/** Central, explainable capability limits. Difficulty never grants private market data. */
public record AiDifficultyPolicy(int publicHistoryRounds, int reviewIntervalRounds,
                                 double signalThreshold, double maxPositionPct,
                                 boolean compareCreditProducts, String preferredModelRoute) {
    public static AiDifficultyPolicy forName(String difficulty) {
        return switch (difficulty == null ? "NORMAL" : difficulty.toUpperCase()) {
            case "EASY" -> new AiDifficultyPolicy(6, 4, .85, 70, false, "FAST");
            case "HARD" -> new AiDifficultyPolicy(50, 1, .35, 100, true, "STRONG_WHEN_COMPLEX");
            default -> new AiDifficultyPolicy(20, 2, .55, 85, false, "FAST_WITH_UPGRADE");
        };
    }
}
