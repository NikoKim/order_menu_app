package com.order.menu.module.entertainment.mode.guessstock.fundamental;

import java.util.SplittableRandom;

/** Shared human/AI research budget. Results are noisy public ranges, never internal exact values. */
public final class ResearchEngine {
    private ResearchEngine() {}

    public static Result perform(Account account, Action action, FundamentalEngine.State fundamentals,
                                 String eventCredibility, double previousOrderImbalancePct, long seed) {
        if (action == null || action == Action.NONE) return new Result(account, Action.NONE, "NONE", 0, 0);
        if (account.remainingPoints() <= 0) throw new IllegalArgumentException("research points exhausted");
        SplittableRandom random = new SplittableRandom(seed);
        double exact = switch (action) {
            case SURVEY_DEMAND -> fundamentals.demandIndex();
            case PREVIEW_REPORT -> FundamentalEngine.metrics(fundamentals).fundamentalScore().doubleValue();
            case CHECK_LIQUIDITY -> previousOrderImbalancePct;
            case VERIFY_EVENT -> credibility(eventCredibility);
            default -> 0;
        };
        double error = action == Action.CHECK_LIQUIDITY ? 12 : action == Action.VERIFY_EVENT ? .35 : 6;
        double center = exact + (random.nextDouble() - .5) * error;
        String direction = center > (action == Action.CHECK_LIQUIDITY ? 8 : action == Action.VERIFY_EVENT ? 1.5 : 103)
                ? "UP" : center < (action == Action.CHECK_LIQUIDITY ? -8 : action == Action.VERIFY_EVENT ? 1.5 : 97)
                ? "DOWN" : "FLAT";
        return new Result(new Account(account.totalPoints(), account.remainingPoints() - 1), action, direction,
                round(center - error / 2), round(center + error / 2));
    }

    private static double credibility(String value) {
        return switch (value == null ? "" : value.toUpperCase()) {
            case "CONFIRMED" -> 3; case "PRELIMINARY" -> 2; default -> 1;
        };
    }
    private static double round(double value) { return Math.round(value * 10d) / 10d; }

    public enum Action { NONE, VERIFY_EVENT, SURVEY_DEMAND, PREVIEW_REPORT, CHECK_LIQUIDITY }
    public record Account(int totalPoints, int remainingPoints) {
        public Account { if (totalPoints < 0 || remainingPoints < 0 || remainingPoints > totalPoints)
            throw new IllegalArgumentException("invalid research account"); }
    }
    public record Result(Account account, Action action, String direction, double rangeLow, double rangeHigh) {}
}
