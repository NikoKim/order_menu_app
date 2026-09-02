package com.order.menu.module.entertainment.mode.guessstock.engine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Single deterministic source of truth for trend and volatility classification. */
public final class MarketStateCalculator {
    private MarketStateCalculator() {}

    public static MarketState calculate(List<BigDecimal> prices, double baseVolatilityPct,
                                        MarketState previousState) {
        List<BigDecimal> clean = prices == null ? List.of() : prices.stream()
                .filter(value -> value != null && value.signum() > 0).toList();
        double return3 = periodReturn(clean, 3);
        double return20 = periodReturn(clean, 20);
        double slope3 = slope(clean, 3);
        double slope8 = slope(clean, 8);
        double realizedVolatility = volatility(clean, 8);
        double referenceVolatility = Math.max(.35, baseVolatilityPct);
        double expectedRealizedVolatility = expectedRealizedVolatility(baseVolatilityPct);
        // Short slopes are per-round values, so the scale restores them to a comparable multi-round signal.
        double trendScore = clamp(1.7 * (.45 * slope3 / referenceVolatility
                + .35 * slope8 / referenceVolatility
                + .20 * (return20 / Math.max(referenceVolatility * 3, 1))), -2, 2);
        TrendState previousTrend = previousState == null ? TrendState.SIDEWAYS : previousState.trend();
        TrendState trend = transition(previousTrend, trendScore);
        VolatilityState volatility = volatilityState(realizedVolatility, expectedRealizedVolatility,
                previousState == null ? null : previousState.volatility());
        int trendAge = previousState != null && trend == previousState.trend() ? previousState.trendAge() + 1 : 1;
        boolean sameRegime = previousState != null && trend == previousState.trend()
                && volatility == previousState.volatility();
        int regimeAge = sameRegime ? previousState.regimeAge() + 1 : 1;
        int consecutive = consecutiveDirection(clean);
        double smoothedVolatility = previousState == null
                ? Math.max(referenceVolatility * .35, realizedVolatility)
                : clamp(.85 * previousState.currentVolatilityPct() + .15 * latestAbsoluteReturn(clean),
                        referenceVolatility * .35, referenceVolatility * 2.5);
        return new MarketState(trend, volatility, regimeAge, trendAge, consecutive, trendScore,
                smoothedVolatility, return3, return20, slope3, slope8, realizedVolatility,
                expectedRealizedVolatility,
                realizedVolatility / Math.max(.01, expectedRealizedVolatility));
    }

    private static TrendState transition(TrendState previous, double score) {
        return switch (previous) {
            case STRONG_UP -> score < .55 ? (score < -.45 ? TrendState.DOWN : TrendState.UP) : TrendState.STRONG_UP;
            case UP -> score >= .95 ? TrendState.STRONG_UP
                    : score < .20 ? (score < -.45 ? TrendState.DOWN : TrendState.SIDEWAYS) : TrendState.UP;
            case STRONG_DOWN -> score > -.55 ? (score > .45 ? TrendState.UP : TrendState.DOWN) : TrendState.STRONG_DOWN;
            case DOWN -> score <= -.95 ? TrendState.STRONG_DOWN
                    : score > -.20 ? (score > .45 ? TrendState.UP : TrendState.SIDEWAYS) : TrendState.DOWN;
            case SIDEWAYS -> score >= 1.20 ? TrendState.STRONG_UP
                    : score >= .45 ? TrendState.UP
                    : score <= -1.20 ? TrendState.STRONG_DOWN
                    : score <= -.45 ? TrendState.DOWN : TrendState.SIDEWAYS;
        };
    }

    static double expectedRealizedVolatility(double baseVolatilityPct) {
        // V2 splits its bounded noise budget across opening and closing. Empirical sigma is
        // therefore far below the configured maximum natural move.
        return Math.max(.18, Math.max(0, baseVolatilityPct) * .30);
    }

    private static VolatilityState volatilityState(double realized, double expected,
                                                    VolatilityState previous) {
        double normalized = realized / Math.max(.01, expected);
        VolatilityState candidate = normalized >= 1.85 ? VolatilityState.EXTREME
                : normalized >= 1.20 ? VolatilityState.HIGH
                : normalized <= .62 ? VolatilityState.LOW : VolatilityState.NORMAL;
        // A shock cools one tier at a time so EXTREME never collapses directly to LOW.
        if (previous == VolatilityState.EXTREME && candidate != VolatilityState.EXTREME) {
            return VolatilityState.HIGH;
        }
        if (previous == VolatilityState.HIGH && candidate == VolatilityState.LOW) {
            return VolatilityState.NORMAL;
        }
        return candidate;
    }

    public static double periodReturn(List<BigDecimal> prices, int period) {
        if (prices == null || prices.size() < 2) return 0;
        int start = Math.max(0, prices.size() - 1 - period);
        double baseline = prices.get(start).doubleValue();
        return baseline <= 0 ? 0 : (prices.get(prices.size() - 1).doubleValue() / baseline - 1) * 100;
    }

    public static double volatility(List<BigDecimal> prices, int period) {
        if (prices == null || prices.size() < 2) return 0;
        int start = Math.max(1, prices.size() - period);
        List<Double> returns = new ArrayList<>();
        for (int index = start; index < prices.size(); index++) {
            double previous = prices.get(index - 1).doubleValue();
            if (previous > 0) returns.add((prices.get(index).doubleValue() / previous - 1) * 100);
        }
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return Math.sqrt(returns.stream().mapToDouble(value -> Math.pow(value - mean, 2)).average().orElse(0));
    }

    public static double slope(List<BigDecimal> prices, int period) {
        if (prices == null || prices.size() < 2) return 0;
        int start = Math.max(0, prices.size() - Math.max(2, period));
        List<BigDecimal> window = prices.subList(start, prices.size());
        double meanX = (window.size() - 1) / 2d;
        double meanY = window.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        double numerator = 0;
        double denominator = 0;
        for (int index = 0; index < window.size(); index++) {
            numerator += (index - meanX) * (window.get(index).doubleValue() - meanY);
            denominator += Math.pow(index - meanX, 2);
        }
        double first = window.get(0).doubleValue();
        return denominator == 0 || first <= 0 ? 0 : numerator / denominator / first * 100;
    }

    private static int consecutiveDirection(List<BigDecimal> prices) {
        if (prices.size() < 2) return 0;
        int direction = Double.compare(prices.get(prices.size() - 1).doubleValue(),
                prices.get(prices.size() - 2).doubleValue());
        if (direction == 0) return 0;
        int count = 0;
        for (int index = prices.size() - 1; index > 0; index--) {
            int current = Double.compare(prices.get(index).doubleValue(), prices.get(index - 1).doubleValue());
            if (current != direction) break;
            count++;
        }
        return direction > 0 ? count : -count;
    }

    private static double latestAbsoluteReturn(List<BigDecimal> prices) {
        if (prices.size() < 2) return 0;
        double previous = prices.get(prices.size() - 2).doubleValue();
        return previous <= 0 ? 0 : Math.abs((prices.get(prices.size() - 1).doubleValue() / previous - 1) * 100);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public enum TrendState { STRONG_UP, UP, SIDEWAYS, DOWN, STRONG_DOWN }
    public enum VolatilityState { LOW, NORMAL, HIGH, EXTREME }

    public record MarketState(TrendState trend, VolatilityState volatility, int regimeAge, int trendAge,
                              int consecutiveDirectionRounds, double trendScore, double currentVolatilityPct,
                              double return3Pct, double return20Pct, double slope3Pct, double slope8Pct,
                              double realizedVolatilityPct, double expectedRealizedVolatilityPct,
                              double normalizedVolatility) {
        public MarketState(TrendState trend, VolatilityState volatility, int regimeAge, int trendAge,
                           int consecutiveDirectionRounds, double trendScore, double currentVolatilityPct,
                           double return3Pct, double return20Pct, double slope3Pct, double slope8Pct,
                           double realizedVolatilityPct) {
            this(trend, volatility, regimeAge, trendAge, consecutiveDirectionRounds, trendScore,
                    currentVolatilityPct, return3Pct, return20Pct, slope3Pct, slope8Pct,
                    realizedVolatilityPct, expectedRealizedVolatility(currentVolatilityPct),
                    realizedVolatilityPct / Math.max(.01, expectedRealizedVolatility(currentVolatilityPct)));
        }
    }
}
