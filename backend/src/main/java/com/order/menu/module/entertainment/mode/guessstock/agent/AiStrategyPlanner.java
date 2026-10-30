package com.order.menu.module.entertainment.mode.guessstock.agent;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/** Public-information tactical execution between model reviews. Never accepts future prices or pending orders. */
public final class AiStrategyPlanner {
    private AiStrategyPlanner() {}

    public static Plan evaluate(Context c, State previous) {
        AiMarketSnapshot s = c.market();
        State old = previous == null ? State.empty(c.maxExposure()) : previous;
        String style = normalizeStyle(c.style());
        double price = s.currentPrice().doubleValue();
        double ema = ema(s.recentPrices(), 20);
        double publicValue = c.publishedFairValue() > 0 ? c.publishedFairValue() : ema;
        double reportWeight = "HARD".equals(c.difficulty()) ? .65 : "EASY".equals(c.difficulty()) ? .15 : .35;
        reportWeight *= Math.exp(-Math.max(0, s.roundNo() - c.valuationAsOfRound()) / 30d);
        double fair = c.publishedFairValue() > 0 ? reportWeight * publicValue + (1 - reportWeight) * ema : ema;
        double uncertainty = Math.max(2, s.volatility20Pct() * 2);
        double fairLow = fair * (1 - uncertainty / 100);
        double fairHigh = fair * (1 + uncertainty / 100);
        double gap = (fair / price - 1) * 100;
        double vol = Math.max(.5, s.volatility8Pct());
        boolean zeroTargetBinding = old.strategicTargetPct() <= .01
                && (old.modelEntryBlocked() || s.roundNo() <= old.lastModelRound());
        double max = old.strategicTargetPct() > .01 || zeroTargetBinding
                ? Math.min(c.maxExposure(), old.strategicTargetPct()) : c.maxExposure();
        boolean continuingPosition = c.shares() > 0 && old.entryRound() > 0;
        double peak = continuingPosition ? Math.max(price, old.peakPrice()) : price;
        int entry = c.shares() > 0 ? (old.entryRound() > 0 ? old.entryRound() : s.roundNo()) : 0;
        double trailingBand = Math.max("TREND".equals(style) ? 8 : "AGGRESSIVE".equals(style) ? 6 : 4,
                vol * ("TREND".equals(style) ? 5 : 3.5));
        double lossBand = Math.max("TREND".equals(style) ? 6 : 4, vol * ("TREND".equals(style) ? 4 : 3));
        double stop = c.shares() > 0 ? Math.max(continuingPosition ? old.stopPrice() : 0, Math.max(c.averageCost() * (1 - lossBand / 100),
                peak * (1 - trailingBand / 100))) : price * (1 - Math.max(4, vol * 3) / 100);
        double profit = c.averageCost() > 0 ? (price / c.averageCost() - 1) * 100 : 0;
        boolean negativeEvent = s.hasPublicEvent() && "DOWN".equals(s.eventDirection());
        boolean positiveEvent = s.hasPublicEvent() && "UP".equals(s.eventDirection());
        boolean fallingKnife = s.return3Pct() < -Math.max(3, vol * 2)
                && s.trendAcceleration3Pct() <= 0 && price <= s.previousClosePrice().doubleValue();
        boolean stabilizing = s.trendAcceleration3Pct() > 0 && s.return3Pct() < 0
                || price > s.previousClosePrice().doubleValue();
        boolean oversold = gap > Math.max(2, vol * 1.5)
                || s.drawdownFromHighPct() < -Math.max(6, vol * 3);
        boolean creditRisk = c.creditRisk() != null && !List.of("SAFE", "NORMAL", "DISABLED").contains(c.creditRisk());
        boolean maturity = c.debt() > 0 && c.maturityRound() > 0 && c.maturityRound() <= s.roundNo() + 1;
        boolean stopHit = c.shares() > 0 && price < stop;
        boolean pendingExit = c.shares() > 0 && "EXIT".equals(old.phase()) && old.tacticalTargetPct() <= .01;
        boolean strategicExit = c.shares() > 0 && max <= .01;
        boolean trendBreak = "TREND".equals(style) && c.shares() > 0
                && s.return3Pct() < -Math.max(1.5, vol * 1.5) && s.slope3Pct() < 0
                && s.return8Pct() < -Math.max(.5, vol) && s.slope8Pct() < 0;
        boolean timedOut = entry > 0 && s.roundNo() - entry >= 12 && profit < Math.max(1, vol)
                && ("PROBE".equals(old.phase()) || "ACCUMULATE".equals(old.phase()));
        boolean risk = stopHit || pendingExit || strategicExit || trendBreak || creditRisk || maturity || timedOut
                || negativeEvent && s.criticalEvent();
        String phase = "OBSERVE";
        String reason = "WAIT_FOR_EDGE";
        double target = Math.min(c.currentExposure(), max);
        double confidence = .65;
        if (risk) {
            phase = "EXIT";
            target = stopHit || pendingExit || strategicExit || trendBreak || timedOut || negativeEvent && s.criticalEvent()
                    ? 0 : Math.min(60, c.currentExposure());
            reason = stopHit ? "TRAILING_STOP" : pendingExit || strategicExit ? "EXIT_PLAN_IN_PROGRESS"
                    : trendBreak ? "TREND_INVALIDATED" : maturity ? "MATURITY_DELEVERAGE"
                    : timedOut ? "PROBE_TIMEOUT" : "RISK_DELEVERAGE";
            confidence = .95;
        } else if (fallingKnife || negativeEvent && gap < 0) {
            phase = c.shares() > 0 ? "DISTRIBUTE" : "OBSERVE";
            target = Math.min(target, Math.max(0, c.currentExposure() - 25));
            reason = "FALLING_KNIFE_NO_AVERAGING";
        } else if (oversold && ("CONTRARIAN".equals(style) || "RANDOM".equals(style) || "AGGRESSIVE".equals(style))) {
            boolean confirmed = stabilizing && !negativeEvent && List.of("PROBE", "ACCUMULATE").contains(old.phase())
                    && s.roundNo() > old.phaseSinceRound();
            phase = confirmed ? "ACCUMULATE" : "PROBE";
            target = confirmed ? Math.min(max, "AGGRESSIVE".equals(style)
                    || "HARD".equals(c.difficulty()) && c.publishedFairValue() > price ? 85 : 65)
                    : Math.min(max, "AGGRESSIVE".equals(style) ? 25 : 15);
            if (c.publishedFairValue() > 0 && price > publicValue * 1.08) target = Math.min(target, 10);
            reason = confirmed ? "BOTTOM_CONFIRMED" : "OVERSOLD_PROBE";
        } else if ((profit > Math.max(4, vol * 3) && s.trendAcceleration3Pct() < 0
                || gap < -Math.max(4, vol * 3))
                && !("TREND".equals(style) && s.return3Pct() > 0 && s.slope8Pct() > 0)) {
            phase = "DISTRIBUTE";
            target = Math.min(max, "DISTRIBUTE".equals(old.phase()) ? old.tacticalTargetPct() : c.currentExposure() * .75);
            reason = "PROFIT_DISTRIBUTION";
        } else if (s.return3Pct() > 0 && s.slope8Pct() >= 0 || positiveEvent && !negativeEvent) {
            phase = "RIDE";
            double desired = switch (style) {
                case "CONSERVATIVE" -> 55;
                case "CONTRARIAN" -> gap > 0 ? 65 : 35;
                case "AGGRESSIVE" -> positiveEvent && gap > vol || s.return3Pct() > vol * 2 && s.slope8Pct() > 0
                        ? Math.min(max, 150) : 100;
                case "TREND" -> 90;
                default -> 75;
            };
            // Difficulty changes evidence use, not access to private state or a bigger bankroll.
            if ("EASY".equals(c.difficulty()) && s.return8Pct() <= 0 && !positiveEvent) desired *= .5;
            target = Math.min(max, desired);
            reason = positiveEvent ? "PUBLIC_CATALYST" : "MULTI_PERIOD_TREND";
        } else if (s.return3Pct() < -vol) {
            phase = "DISTRIBUTE";
            target = Math.min(max, "CONSERVATIVE".equals(style) ? 10 : 25);
            reason = "TREND_WEAKENED";
        }

        double edge = Math.max(0, gap) + Math.max(0, s.return3Pct()) * .35 + (positiveEvent ? vol : 0);
        boolean confirmedTrend = s.return3Pct() > 1 && s.return8Pct() > 0 && s.slope3Pct() > 0;
        if ("HARD".equals(c.difficulty()) && confirmedTrend && List.of("TREND", "RANDOM", "AGGRESSIVE").contains(style)) {
            edge = Math.max(edge, s.return3Pct() * .85 + Math.max(0, s.return8Pct()) * .15);
            if (!risk && !negativeEvent && !"DISTRIBUTE".equals(phase)) {
                phase = "RIDE";
                target = Math.min(max, "RANDOM".equals(style) ? 90 : 100);
                reason = "CONFIRMED_TREND_CONTINUATION";
            }
        }
        double participation = c.marketCapacity() <= 0 ? 0
                : Math.abs(target - c.currentExposure()) / 100 * c.netAssets() / price / c.marketCapacity();
        double ownImpact = Math.min(c.maxImpactPct(), participation * c.maxImpactPct());
        double cost = 2 * (c.feePct() + c.slippagePct()) + ownImpact * 1.5
                + (target > 100 ? c.interestRatePct() * 3 : 0);
        // Flow only comes from settled public trades. Crowding changes execution pace, not market truth.
        if (target > c.currentExposure() && c.publicBuyConcentration() > .65 && gap < vol) {
            target = Math.min(target, c.currentExposure() + 10);
            reason = "CROWDED_ENTRY_SPLIT";
        }
        if (target > c.currentExposure() && oversold && stabilizing && c.publicSellConcentration() > .65) {
            target = Math.min(target, c.currentExposure() + 15);
            reason = "ABSORB_CONCENTRATED_SELLS";
        }
        if (!risk && target < c.currentExposure() && participation > .15) {
            target = Math.max(target, c.currentExposure() - 15);
            reason = "DISTRIBUTE_WITH_LIQUIDITY";
        }
        if (target > c.currentExposure() && edge <= cost + .4) {
            target = c.currentExposure();
            phase = "OBSERVE";
            reason = "COST_EXCEEDS_EDGE";
        }
        if (target > 100 && (s.remainingRounds() < 3 || edge < cost + vol)) target = 100;
        target = clamp(target, 0, c.maxExposure());
        int since = phase.equals(old.phase()) ? old.phaseSinceRound() : s.roundNo();
        State next = new State(phase, since, entry, peak, stop, fairLow, fairHigh,
                old.lastModelRound(), old.strategicTargetPct(), c.reportRound(), c.researchId(), target, old.modelReason(),
                old.modelEntryBlocked());
        boolean transition = !phase.equals(old.phase());
        double maxBuyPrice = ("TREND".equals(style) ? Math.max(price, fairHigh) : fairHigh)
                * (1 + Math.max(1, vol) / 100);
        double minSellPrice = risk ? 0 : price * (1 - Math.max(2, vol * 2) / 100);
        boolean strongEntry = "HARD".equals(c.difficulty()) && confirmedTrend && target > c.currentExposure()
                && edge > cost + Math.max(.8, vol);
        return new Plan(next, target, risk || strongEntry ? "HIGH" : "MEDIUM", risk, transition,
                confidence, reason, minSellPrice, maxBuyPrice, cost, edge);
    }

    private static String normalizeStyle(String style) {
        return switch (style == null ? "" : style.toUpperCase(Locale.ROOT)) {
            case "稳健", "CONSERVATIVE" -> "CONSERVATIVE";
            case "趋势", "TREND" -> "TREND";
            case "逆势", "CONTRARIAN" -> "CONTRARIAN";
            case "激进", "AGGRESSIVE" -> "AGGRESSIVE";
            default -> "RANDOM";
        };
    }

    public static Plan exitInvalidatedThesis(Plan plan, int round) {
        State s = plan.state();
        State exit = new State("EXIT", round, s.entryRound(), s.peakPrice(), s.stopPrice(), s.fairLow(), s.fairHigh(),
                s.lastModelRound(), s.strategicTargetPct(), s.reportRound(), s.researchId(), 0, s.modelReason(),
                s.modelEntryBlocked());
        return new Plan(exit, 0, "HIGH", true, true, .95, "THESIS_INVALIDATED", 0,
                plan.maxBuyPrice(), plan.estimatedRoundTripCostPct(), plan.expectedEdgePct());
    }

    private static double ema(List<BigDecimal> values, int period) {
        if (values.isEmpty()) return 0;
        double average = values.get(Math.max(0, values.size() - period)).doubleValue();
        double alpha = 2d / (Math.min(period, values.size()) + 1);
        for (int i = Math.max(0, values.size() - period) + 1; i < values.size(); i++) {
            average += alpha * (values.get(i).doubleValue() - average);
        }
        return average;
    }

    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }

    public record Context(AiMarketSnapshot market, String style, String difficulty, double currentExposure,
                          double maxExposure, long shares, double averageCost, double netAssets,
                          double publishedFairValue, int reportRound, long researchId, double debt,
                          String creditRisk, int maturityRound, double feePct, double slippagePct,
                          double interestRatePct, double marketCapacity, double maxImpactPct,
                          double publicBuyConcentration, double publicSellConcentration, int valuationAsOfRound) {
        public Context(AiMarketSnapshot market, String style, String difficulty, double currentExposure,
                       double maxExposure, long shares, double averageCost, double netAssets,
                       double publishedFairValue, int reportRound, long researchId, double debt,
                       String creditRisk, int maturityRound, double feePct, double slippagePct,
                       double interestRatePct, double marketCapacity, double maxImpactPct,
                       double publicBuyConcentration, double publicSellConcentration) {
            this(market, style, difficulty, currentExposure, maxExposure, shares, averageCost, netAssets,
                    publishedFairValue, reportRound, researchId, debt, creditRisk, maturityRound,
                    feePct, slippagePct, interestRatePct, marketCapacity, maxImpactPct,
                    publicBuyConcentration, publicSellConcentration, reportRound);
        }
        public Context(AiMarketSnapshot market, String style, String difficulty, double currentExposure,
                       double maxExposure, long shares, double averageCost, double netAssets,
                       double publishedFairValue, int reportRound, long researchId, double debt,
                       String creditRisk, int maturityRound, double feePct, double slippagePct,
                       double interestRatePct, double marketCapacity, double maxImpactPct,
                       double publicBuyConcentration) {
            this(market, style, difficulty, currentExposure, maxExposure, shares, averageCost, netAssets,
                    publishedFairValue, reportRound, researchId, debt, creditRisk, maturityRound,
                    feePct, slippagePct, interestRatePct, marketCapacity, maxImpactPct, publicBuyConcentration, 0, reportRound);
        }
    }

    public record State(String phase, int phaseSinceRound, int entryRound, double peakPrice, double stopPrice,
                        double fairLow, double fairHigh, int lastModelRound, double strategicTargetPct,
                        int reportRound, long researchId, double tacticalTargetPct, String modelReason,
                        boolean modelEntryBlocked) {
        public State { modelReason = modelReason == null ? "" : modelReason; }
        public State(String phase, int phaseSinceRound, int entryRound, double peakPrice, double stopPrice,
                     double fairLow, double fairHigh, int lastModelRound, double strategicTargetPct,
                     int reportRound, long researchId, double tacticalTargetPct) {
            this(phase, phaseSinceRound, entryRound, peakPrice, stopPrice, fairLow, fairHigh,
                    lastModelRound, strategicTargetPct, reportRound, researchId, tacticalTargetPct, "", false);
        }
        public static State empty(double maxExposure) {
            return new State("OBSERVE", 0, 0, 0, 0, 0, 0, 0, maxExposure, 0, 0, 0);
        }
        public State withModel(int round, double target) {
            return withModel(round, target, modelReason);
        }
        public State withModel(int round, double target, String reason) {
            return withModel(round, target, reason, modelEntryBlocked);
        }
        public State withModel(int round, double target, String reason, boolean entryBlocked) {
            return new State(phase, phaseSinceRound, entryRound, peakPrice, stopPrice, fairLow, fairHigh,
                    round, target, reportRound, researchId, tacticalTargetPct, reason, target <= .01 && entryBlocked);
        }
    }

    public record Plan(State state, double target, String urgency, boolean risk, boolean transition,
                       double confidence, String reasonCode, double minSellPrice, double maxBuyPrice,
                       double estimatedRoundTripCostPct, double expectedEdgePct) {}
}
