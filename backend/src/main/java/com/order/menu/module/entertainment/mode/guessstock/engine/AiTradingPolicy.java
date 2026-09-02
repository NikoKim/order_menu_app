package com.order.menu.module.entertainment.mode.guessstock.engine;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/** Deterministic policy layer. Model output may refine it, but cannot bypass its price discipline. */
public final class AiTradingPolicy {
    private AiTradingPolicy() {}

    public static Decision decide(Context context) {
        MarketFeatures market = features(context);
        String style = normalizedStyle(context.style(), market.regime());
        double remainingRatio = (context.totalRounds() - context.roundNo() + 1.0) / context.totalRounds();
        double rankPressure = context.participantCount() <= 1 ? 0
                : (context.returnRank() - 1.0) / (context.participantCount() - 1.0);
        double event = publicEventSentiment(context.eventName(), context.eventDescription())
                * eventInformationMultiplier(context.eventRelevance(), context.eventCredibility(),
                context.eventSurprise(), context.eventStrength());
        double flow = clamp((context.marketBuyQuantity() - context.marketSellQuantity())
                / Math.max(1.0, context.marketLiquidityShares() * context.participantCount()), -1, 1);
        double activityNoise = (unitNoise(context.personalitySeed(), context.roundNo(), context.stage()) - .5) * .24;

        if ("OPENING".equals(context.stage())) {
            return openingDecision(context, market, style, remainingRatio, rankPressure, event, flow, activityNoise);
        }
        return closingDecision(context, market, style, remainingRatio, rankPressure, event, flow, activityNoise);
    }

    private static Decision openingDecision(Context context, MarketFeatures market, String style,
                                             double remaining, double rankPressure, double event,
                                             double flow, double noise) {
        double value = clamp(-market.pricePosition() / 1.6, -1.5, 1.5);
        double trend = clamp(market.momentumPercent() / Math.max(.7, market.volatilityPercent()), -1.5, 1.5);
        double stability = market.fallDecelerating() ? .75 : market.momentumPercent() >= 0 ? .25 : -.35;
        double score = switch (style) {
            case "CONSERVATIVE" -> 1.15 * value + .42 * trend + .82 * stability + .35 * event - .20 * flow;
            case "TREND" -> .25 * value + 1.28 * trend + .48 * stability + .48 * event + .38 * flow;
            case "CONTRARIAN" -> 1.48 * value - .18 * trend + .95 * stability + .30 * event - .18 * flow;
            case "AGGRESSIVE" -> .48 * value + 1.05 * trend + .58 * stability + .72 * event + .48 * flow;
            default -> .72 * value + .72 * trend + .60 * stability + .50 * event + .16 * flow;
        };

        boolean chasing = market.pricePosition() > .85 && market.momentumPercent() > 0;
        boolean fallingKnife = market.pricePosition() < -.75 && market.momentumPercent() < 0
                && !market.fallDecelerating();
        if (fallingKnife && market.momentumPercent() < -1) {
            return new Decision("HOLD", 0, .85, market.regime(), market,
                    "价格虽低但跌势仍在加速，等待企稳后再分批买入");
        }
        if (chasing) score -= .75 + .28 * market.consecutiveUpRounds() + .35 * market.pricePosition();
        if (fallingKnife) score -= .85 + Math.min(.55, Math.abs(market.momentumPercent()) / 5);
        if (context.memory().losingBuyRate() > .45 && (chasing || market.pricePosition() > .4)) {
            score -= .45 * context.memory().losingBuyRate();
        }
        if (rankPressure > .55 && remaining < .35) score += .18 + .30 * rankPressure;
        if (rankPressure < .25 && remaining < .45) score -= .12;
        score += noise;

        double threshold = threshold(context.difficulty(), context.revision());
        if (score <= threshold) {
            String reason = chasing ? "价格处于近期高位且连续走强，避免追高"
                    : fallingKnife ? "价格虽低但跌势仍在加速，等待企稳"
                    : "估值、趋势和公开事件信号不足，保留现金";
            return new Decision("HOLD", 0, confidence(score, threshold), market.regime(), market, reason);
        }
        double confidence = confidence(score, threshold);
        double risk = (.16 + .62 * confidence) * styleRisk(style);
        if (rankPressure > .55 && remaining < .35) risk *= 1.15;
        if (context.memory().losingBuyRate() > .5) risk *= .78;
        risk = clamp(risk, .08, .92);
        return new Decision("BUY", risk, confidence, market.regime(), market,
                "%s，价格位置 %.2f，趋势动量 %+.2f%%，按置信度分批买入".formatted(
                        regimeName(market.regime()), market.pricePosition(), market.momentumPercent()));
    }

    private static Decision closingDecision(Context context, MarketFeatures market, String style,
                                             double remaining, double rankPressure, double event,
                                             double flow, double noise) {
        if (context.quantity() <= 0) {
            return new Decision("HOLD", 0, 1, market.regime(), market, "当前没有持仓，无需卖出");
        }
        double profit = context.averageCost().signum() <= 0 ? 0
                : context.referencePrice().subtract(context.averageCost())
                .divide(context.averageCost(), 8, java.math.RoundingMode.HALF_UP).doubleValue() * 100;
        double trendWeakness = clamp(-market.momentumPercent() / Math.max(.7, market.volatilityPercent()), -1.5, 1.5);
        double overheat = clamp(market.pricePosition() / 1.4, -1.5, 1.5);
        double urgency = remaining <= .18 ? 1.25 : remaining <= .35 ? .55 : 0;
        double score = switch (style) {
            case "CONSERVATIVE" -> .62 * positive(profit / 4) + .95 * trendWeakness + .72 * overheat - .55 * event + urgency;
            case "TREND" -> .35 * positive(profit / 5) + 1.35 * trendWeakness + .45 * overheat - .48 * event + urgency;
            case "CONTRARIAN" -> .30 * positive(profit / 5) + .35 * trendWeakness + 1.28 * overheat - .35 * event + urgency;
            case "AGGRESSIVE" -> .28 * positive(profit / 6) + 1.05 * trendWeakness + .55 * overheat - .62 * event + .85 * urgency;
            default -> .42 * positive(profit / 5) + .88 * trendWeakness + .78 * overheat - .46 * event + urgency;
        };
        boolean stopLoss = profit < -Math.max(2.0, market.volatilityPercent() * 1.15)
                && market.momentumPercent() < 0;
        boolean panicZone = market.pricePosition() < -1 && market.fallDecelerating() && !stopLoss;
        if (stopLoss) score += .75;
        if (panicZone) score -= .85;
        if (rankPressure > .55 && remaining < .30) score -= .12;
        if (rankPressure < .25 && profit > 0) score += .22;
        score -= Math.max(0, -flow) * .25;
        score += noise;

        double threshold = threshold(context.difficulty(), context.revision());
        if (score <= threshold) {
            String reason = panicZone ? "价格位于低位且跌势放缓，避免恐慌卖出"
                    : "趋势尚未破坏且卖出优势不足，继续持有";
            return new Decision("HOLD", 0, confidence(score, threshold), market.regime(), market, reason);
        }
        double confidence = confidence(score, threshold);
        double risk = (.18 + .68 * confidence) * styleRisk(style);
        if (stopLoss || remaining <= .18) risk = Math.max(risk, .72);
        if (rankPressure < .25 && profit > 0) risk *= 1.10;
        risk = clamp(risk, .10, .96);
        return new Decision("SELL", risk, confidence, market.regime(), market,
                "%s，持仓收益 %+.2f%%，趋势动量 %+.2f%%，按风险分批卖出".formatted(
                        stopLoss ? "止损信号成立" : regimeName(market.regime()), profit, market.momentumPercent()));
    }

    static MarketFeatures features(Context context) {
        List<BigDecimal> statePrices = new java.util.ArrayList<>(context.history().stream()
                .map(MarketPoint::closePrice).filter(java.util.Objects::nonNull).toList());
        if (statePrices.isEmpty() || statePrices.get(statePrices.size() - 1).compareTo(context.referencePrice()) != 0) {
            statePrices.add(context.referencePrice());
        }
        MarketStateCalculator.MarketState state = MarketStateCalculator.calculate(statePrices,
                context.baseVolatilityPercent(), null);
        List<Double> prices = context.history().stream().map(point -> point.closePrice().doubleValue()).toList();
        double current = context.referencePrice().doubleValue();
        double mean = prices.isEmpty() ? current : prices.stream().mapToDouble(Double::doubleValue).average().orElse(current);
        double variance = prices.size() < 2 ? Math.pow(Math.max(current * .01, .01), 2)
                : prices.stream().mapToDouble(value -> Math.pow(value - mean, 2)).average().orElse(0);
        double deviation = Math.max(Math.sqrt(variance), Math.max(current * .006, .01));
        double position = (current - mean) / deviation;
        double previous = prices.isEmpty() ? current : prices.get(prices.size() - 1);
        double momentum = previous == 0 ? 0 : (current / previous - 1) * 100;
        if (prices.size() >= 3 && "OPENING".equals(context.stage())) {
            double prior = prices.get(prices.size() - 3);
            momentum = prior == 0 ? momentum : (current / prior - 1) * 50;
        }
        double[] returns = new double[Math.max(0, prices.size() - 1)];
        for (int i = 1; i < prices.size(); i++) returns[i - 1] = (prices.get(i) / prices.get(i - 1) - 1) * 100;
        double volatility = Math.max(.1, state.currentVolatilityPct());
        int consecutiveUp = 0;
        for (int i = returns.length - 1; i >= 0 && returns[i] > 0; i--) consecutiveUp++;
        boolean decelerating = returns.length >= 2 && returns[returns.length - 2] < 0
                && returns[returns.length - 1] > returns[returns.length - 2];
        Regime regime;
        if (state.volatility() == MarketStateCalculator.VolatilityState.EXTREME) regime = Regime.HIGH_VOLATILITY;
        else if (position > 1.15 && momentum > 0) regime = Regime.OVERHEATED;
        else if (position < -1.15) regime = Regime.OVERSOLD;
        else regime = switch (state.trend()) {
            case STRONG_UP, UP -> Regime.BULL;
            case STRONG_DOWN, DOWN -> Regime.BEAR;
            default -> Regime.SIDEWAYS;
        };
        return new MarketFeatures(regime, position, momentum, volatility, consecutiveUp, decelerating);
    }

    static double publicEventSentiment(String name, String description) {
        String text = ((name == null ? "" : name) + " " + (description == null ? "" : description)).toLowerCase(Locale.ROOT);
        String[] positive = {"增长", "上涨", "突破", "回暖", "加速", "改善", "追加", "超预期", "扶持", "提价", "放宽", "量产"};
        String[] negative = {"下跌", "下滑", "削减", "中断", "失败", "收紧", "禁售", "事故", "造假", "召回", "终止", "过剩", "风险"};
        int score = 0;
        for (String word : positive) if (text.contains(word)) score++;
        for (String word : negative) if (text.contains(word)) score--;
        return clamp(score / 2.0, -1, 1);
    }

    static double eventInformationMultiplier(String relevance, String credibility,
                                             String surprise, String strength) {
        double relevanceFactor = switch (relevance == null ? "MEDIUM" : relevance) {
            case "LOW" -> .55; case "HIGH" -> 1.25; default -> 1.0;
        };
        double credibilityFactor = switch (credibility == null ? "CONFIRMED" : credibility) {
            case "RUMOR" -> .55; case "PRELIMINARY" -> .85; default -> 1.05;
        };
        double surpriseFactor = switch (surprise == null ? "NORMAL" : surprise) {
            case "EXPECTED" -> .65; case "MAJOR" -> 1.25; default -> 1.0;
        };
        double strengthFactor = switch (strength == null ? "MEDIUM" : strength) {
            case "WEAK" -> .70; case "STRONG" -> 1.20; default -> 1.0;
        };
        return relevanceFactor * credibilityFactor * surpriseFactor * strengthFactor;
    }

    private static String normalizedStyle(String style, Regime regime) {
        String normalized = style == null ? "RANDOM" : style.toUpperCase(Locale.ROOT);
        if (!"RANDOM".equals(normalized)) return normalized;
        return switch (regime) {
            case BULL, BEAR -> "TREND";
            case OVERHEATED, OVERSOLD -> "CONTRARIAN";
            case HIGH_VOLATILITY -> "CONSERVATIVE";
            default -> "RANDOM";
        };
    }

    private static double threshold(String difficulty, boolean revision) {
        double value = switch (difficulty == null ? "NORMAL" : difficulty) {
            case "EASY" -> .38;
            case "HARD" -> .10;
            default -> .22;
        };
        return revision ? value + .10 : value;
    }

    private static double styleRisk(String style) {
        return switch (style) {
            case "CONSERVATIVE" -> .58;
            case "AGGRESSIVE" -> 1.18;
            case "TREND" -> .88;
            case "CONTRARIAN" -> .78;
            default -> .82;
        };
    }

    private static double confidence(double score, double threshold) {
        return clamp((score - threshold + .45) / 1.65, .05, 1);
    }

    private static double unitNoise(long seed, int round, String stage) {
        long value = seed ^ (round * 0x9E3779B97F4A7C15L) ^ ("OPENING".equals(stage) ? 0x51L : 0xA7L);
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        return (value >>> 11) * 0x1.0p-53;
    }

    private static double positive(double value) { return Math.max(0, value); }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private static String regimeName(Regime regime) {
        return switch (regime) {
            case BULL -> "上涨趋势"; case BEAR -> "下跌趋势"; case OVERHEATED -> "价格过热";
            case OVERSOLD -> "价格超跌"; case HIGH_VOLATILITY -> "高波动市场"; default -> "震荡市场";
        };
    }

    public enum Regime { BULL, BEAR, SIDEWAYS, OVERHEATED, OVERSOLD, HIGH_VOLATILITY }
    public record MarketPoint(BigDecimal openPrice, BigDecimal closePrice) {}
    public record Memory(int recentBuyCount, int losingBuyCount, int recentSellCount) {
        public double losingBuyRate() { return recentBuyCount == 0 ? 0 : losingBuyCount / (double) recentBuyCount; }
    }
    public record Context(String style, String difficulty, int roundNo, int totalRounds, String stage,
                          BigDecimal referencePrice, BigDecimal averageCost, BigDecimal cash, long quantity,
                          List<MarketPoint> history, long marketBuyQuantity, long marketSellQuantity,
                          int returnRank, int participantCount, String eventName, String eventDescription,
                          String eventType, String eventRelevance, String eventCredibility,
                          String eventSurprise, String eventStrength,
                          double baseVolatilityPercent, double marketLiquidityShares, Memory memory,
                          long personalitySeed, boolean revision) {
        public Context(String style, String difficulty, int roundNo, int totalRounds, String stage,
                       BigDecimal referencePrice, BigDecimal averageCost, BigDecimal cash, long quantity,
                       List<MarketPoint> history, long marketBuyQuantity, long marketSellQuantity,
                       int returnRank, int participantCount, String eventName, String eventDescription,
                       double baseVolatilityPercent, double marketLiquidityShares, Memory memory,
                       long personalitySeed, boolean revision) {
            this(style, difficulty, roundNo, totalRounds, stage, referencePrice, averageCost, cash, quantity,
                    history, marketBuyQuantity, marketSellQuantity, returnRank, participantCount,
                    eventName, eventDescription, "INDUSTRY", "MEDIUM", "CONFIRMED", "NORMAL", "MEDIUM",
                    baseVolatilityPercent, marketLiquidityShares, memory, personalitySeed, revision);
        }
    }
    public record MarketFeatures(Regime regime, double pricePosition, double momentumPercent,
                                 double volatilityPercent, int consecutiveUpRounds, boolean fallDecelerating) {}
    public record Decision(String action, double riskFraction, double confidence, Regime regime,
                           MarketFeatures features, String reason) {}
}
