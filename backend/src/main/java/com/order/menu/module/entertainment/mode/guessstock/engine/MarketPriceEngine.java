package com.order.menu.module.entertainment.mode.guessstock.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.SplittableRandom;

/** Version 2 log-return price formation with fatigue, medium reversion and volatility memory. */
public final class MarketPriceEngine {
    private MarketPriceEngine() {}

    public static Result price(Context context) {
        double baseVolatility = Math.max(0, context.baseVolatilityPct());
        double stageBudget = context.opening() ? .35 : .65;
        double noiseScale = Math.max(baseVolatility * .35, context.state().currentVolatilityPct()) * stageBudget;
        double noise = truncatedGaussian(new SplittableRandom(context.seed()), noiseScale);
        double direction = switch (context.state().trend()) {
            case STRONG_UP, UP -> 1;
            case STRONG_DOWN, DOWN -> -1;
            default -> 0;
        };
        double rawMomentum = direction * Math.min(.45, baseVolatility * .10);
        double timeDecay = Math.exp(-Math.max(0, context.state().trendAge()) / 6d);
        double exhaustion = clamp(Math.abs(context.state().consecutiveDirectionRounds()) / 10d
                + Math.abs(context.state().return20Pct()) / 30d, 0, 1);
        double momentum = rawMomentum * timeDecay * (1 - .65 * exhaustion) * stageBudget;
        double meanReversion = meanReversion(context.historyPrices(), context.referencePrice(),
                context.state(), stageBudget);
        double regime = 0;
        double rawTotal = context.baseDriftPct() + regime + momentum + context.eventImpactPct()
                + meanReversion + noise + context.orderFlowImpactPct() + context.fundamentalImpactPct()
                + context.reportSurprisePct();
        double total = clamp(rawTotal,
                -Math.abs(context.maxMovePct()), Math.abs(context.maxMovePct()));
        BigDecimal price = context.referencePrice().multiply(BigDecimal.valueOf(Math.exp(total / 100d)))
                .max(new BigDecimal("0.01")).setScale(4, RoundingMode.HALF_UP);
        BigDecimal baseValue = rate(context.baseDriftPct());
        BigDecimal momentumValue = rate(momentum);
        BigDecimal eventValue = rate(context.eventImpactPct());
        BigDecimal reversionValue = rate(meanReversion);
        BigDecimal noiseValue = rate(noise);
        BigDecimal flowValue = rate(context.orderFlowImpactPct());
        BigDecimal fundamentalValue = rate(context.fundamentalImpactPct());
        BigDecimal reportValue = rate(context.reportSurprisePct());
        BigDecimal rawTotalValue = baseValue.add(momentumValue).add(eventValue)
                .add(reversionValue).add(noiseValue).add(flowValue).add(fundamentalValue).add(reportValue)
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal totalValue = rate(total);
        BigDecimal clampAdjustment = totalValue.subtract(rawTotalValue).setScale(4, RoundingMode.HALF_UP);
        return new Result(price, baseValue, rate(regime), momentumValue,
                eventValue, reversionValue, noiseValue, flowValue, fundamentalValue, reportValue,
                rawTotalValue, clampAdjustment, totalValue);
    }

    private static double meanReversion(List<BigDecimal> prices, BigDecimal current,
                                        MarketStateCalculator.MarketState state, double stageBudget) {
        if (prices == null || prices.size() < 3 || current == null || current.signum() <= 0) return 0;
        double ema20 = ema(prices, 20);
        double ema50 = prices.size() >= 20 ? ema(prices, 50) : ema20;
        double fair = ema20 * .7 + ema50 * .3;
        if (fair <= 0) return 0;
        double deviation = Math.log(fair / current.doubleValue()) * 100;
        boolean strongTrend = state.trend() == MarketStateCalculator.TrendState.STRONG_UP
                || state.trend() == MarketStateCalculator.TrendState.STRONG_DOWN;
        double trendFactor = strongTrend ? .5 : Math.min(1, .5 + state.trendAge() / 12d);
        return clamp(deviation * .04 * trendFactor * stageBudget, -.60 * stageBudget, .60 * stageBudget);
    }

    private static double ema(List<BigDecimal> values, int period) {
        int start = Math.max(0, values.size() - period);
        double alpha = 2d / (Math.min(period, values.size() - start) + 1d);
        double ema = values.get(start).doubleValue();
        for (int index = start + 1; index < values.size(); index++) {
            ema = alpha * values.get(index).doubleValue() + (1 - alpha) * ema;
        }
        return ema;
    }

    private static double truncatedGaussian(SplittableRandom random, double maximumAbsolute) {
        if (maximumAbsolute <= 0) return 0;
        double u1 = Math.max(1.0e-12, random.nextDouble());
        double gaussian = Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * random.nextDouble());
        return clamp(gaussian * maximumAbsolute / 2d, -maximumAbsolute, maximumAbsolute);
    }

    private static BigDecimal rate(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }

    public record Context(BigDecimal referencePrice, List<BigDecimal> historyPrices,
                          MarketStateCalculator.MarketState state, double baseVolatilityPct,
                          double baseDriftPct, double eventImpactPct, double orderFlowImpactPct,
                          double maxMovePct, boolean opening, long seed,
                          double fundamentalImpactPct, double reportSurprisePct) implements InternalPricingContext {
        public Context(BigDecimal referencePrice, List<BigDecimal> historyPrices,
                       MarketStateCalculator.MarketState state, double baseVolatilityPct,
                       double baseDriftPct, double eventImpactPct, double orderFlowImpactPct,
                       double maxMovePct, boolean opening, long seed) {
            this(referencePrice, historyPrices, state, baseVolatilityPct, baseDriftPct, eventImpactPct,
                    orderFlowImpactPct, maxMovePct, opening, seed, 0, 0);
        }
    }

    public record Result(BigDecimal price, BigDecimal baseDriftPct, BigDecimal regimeImpactPct,
                         BigDecimal momentumImpactPct, BigDecimal eventImpactPct,
                         BigDecimal meanReversionImpactPct, BigDecimal noiseImpactPct,
                         BigDecimal orderFlowImpactPct, BigDecimal fundamentalImpactPct,
                         BigDecimal reportSurprisePct, BigDecimal rawTotalImpactPct,
                         BigDecimal clampAdjustmentPct, BigDecimal totalImpactPct) {}
}
