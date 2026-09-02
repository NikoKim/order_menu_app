package com.order.menu.module.entertainment.mode.guessstock.engine;

import com.order.menu.module.entertainment.mode.guessstock.config.GuessStockConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.SplittableRandom;

public final class GuessStockEventImpactEngine {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4);

    private GuessStockEventImpactEngine() {}

    public record Context(GuessStockConfig.EventConfig event, String fixedDirection,
                          BigDecimal baseChangePercent, BigDecimal baseVolatilityPercent,
                          BigDecimal referencePrice, List<BigDecimal> recentClosePrices, long seed,
                          MarketStateCalculator.MarketState marketState) {
        public Context(GuessStockConfig.EventConfig event, String fixedDirection,
                       BigDecimal baseChangePercent, BigDecimal baseVolatilityPercent,
                       BigDecimal referencePrice, List<BigDecimal> recentClosePrices, long seed) {
            this(event, fixedDirection, baseChangePercent, baseVolatilityPercent, referencePrice,
                    recentClosePrices, seed, null);
        }
    }

    public record Result(boolean effective, String direction, String tier, String regime,
                         BigDecimal rawChangePercent, BigDecimal finalChangePercent,
                         BigDecimal adaptationPercent, String reason) {}

    public static Result evaluate(Context context) {
        GuessStockConfig.EventConfig event = context.event();
        SplittableRandom random = new SplittableRandom(context.seed());
        String regime = context.marketState() == null
                ? detectRegime(context.referencePrice(), context.recentClosePrices(), context.baseVolatilityPercent())
                : regime(context.marketState());
        String direction = resolveDirection(event.direction(), context.fixedDirection(), regime, random);
        double probability = event.effectProbability().doubleValue();
        double roll = random.nextDouble(100.0);
        String tier = tier(roll, probability);
        if ("NONE".equals(tier)) {
            return new Result(false, direction, tier, regime, ZERO, ZERO, ZERO,
                    "市场未形成一致定价，本阶段没有产生可观察的事件影响。");
        }

        BigDecimal sampled = truncatedNormal(random, event.minImpactPercent(), event.maxImpactPercent());
        double factor = tierMultiplier(tier)
                * metadataMultiplier(event)
                * regimeMultiplier(regime, direction)
                * cohesionMultiplier(context.baseChangePercent(), direction);
        double volatility = Math.max(0.05, context.baseVolatilityPercent().doubleValue());
        double volatilityCap = volatility * switch (tier) {
            case "WEAK" -> 1.10;
            case "NORMAL" -> 2.20;
            default -> 3.20;
        };
        double magnitude = Math.min(event.maxImpactPercent().doubleValue(),
                Math.min(sampled.doubleValue() * factor, volatilityCap));
        BigDecimal finalChange = rate(BigDecimal.valueOf("DOWN".equals(direction) ? -magnitude : magnitude));
        BigDecimal rawChange = rate("DOWN".equals(direction) ? sampled.negate() : sampled);
        BigDecimal adaptation = sampled.signum() == 0 ? ZERO
                : rate(BigDecimal.valueOf(magnitude / sampled.doubleValue() * 100.0));
        return new Result(true, direction, tier, regime, rawChange, finalChange, adaptation,
                reason(event, regime, direction, tier, factor));
    }

    public static String detectRegime(BigDecimal referencePrice, List<BigDecimal> recentClosePrices,
                                      BigDecimal baseVolatilityPercent) {
        if (referencePrice == null || recentClosePrices == null || recentClosePrices.size() < 2) return "SIDEWAYS";
        List<BigDecimal> clean = recentClosePrices.stream().filter(value -> value != null && value.signum() > 0).toList();
        List<Double> prices = clean.stream().map(BigDecimal::doubleValue).toList();
        double reference = referencePrice.doubleValue();
        double mean = prices.stream().mapToDouble(Double::doubleValue).average().orElse(reference);
        double variance = prices.stream().mapToDouble(value -> Math.pow(value - mean, 2)).average().orElse(0.0);
        double deviation = Math.max(Math.sqrt(variance), Math.max(reference * 0.006, 0.01));
        double position = (reference - mean) / deviation;
        List<BigDecimal> withReference = new java.util.ArrayList<>(clean);
        if (withReference.isEmpty() || withReference.get(withReference.size() - 1).compareTo(referencePrice) != 0) {
            withReference.add(referencePrice);
        }
        MarketStateCalculator.MarketState state = MarketStateCalculator.calculate(withReference,
                Math.max(.1, baseVolatilityPercent.doubleValue()), null);
        if (position > 1.15 && state.trend().ordinal() < MarketStateCalculator.TrendState.SIDEWAYS.ordinal()) {
            return "OVERHEATED";
        }
        if (position < -1.15 && state.trend().ordinal() > MarketStateCalculator.TrendState.SIDEWAYS.ordinal()) {
            return "OVERSOLD";
        }
        return regime(state);
    }

    private static String regime(MarketStateCalculator.MarketState state) {
        if (state.volatility() == MarketStateCalculator.VolatilityState.EXTREME
                || state.volatility() == MarketStateCalculator.VolatilityState.HIGH) return "HIGH_VOLATILITY";
        return switch (state.trend()) {
            case STRONG_UP, UP -> "BULL";
            case STRONG_DOWN, DOWN -> "BEAR";
            default -> "SIDEWAYS";
        };
    }

    private static String resolveDirection(String configured, String fixed, String regime, SplittableRandom random) {
        if (fixed != null && !fixed.isBlank()) return fixed;
        if (!"RANDOM".equals(configured)) return configured;
        return switch (regime) {
            case "OVERHEATED" -> "DOWN";
            case "OVERSOLD" -> "UP";
            case "BULL" -> random.nextDouble() < 0.65 ? "UP" : "DOWN";
            case "BEAR" -> random.nextDouble() < 0.65 ? "DOWN" : "UP";
            default -> random.nextBoolean() ? "UP" : "DOWN";
        };
    }

    private static String tier(double roll, double probability) {
        if (probability <= 0 || roll >= probability) return "NONE";
        double percentile = roll / probability;
        if (percentile < 0.50) return "WEAK";
        if (percentile < 0.85) return "NORMAL";
        return "STRONG";
    }

    private static BigDecimal truncatedNormal(SplittableRandom random, BigDecimal minimum, BigDecimal maximum) {
        if (minimum.compareTo(maximum) == 0) return rate(minimum);
        double min = minimum.doubleValue();
        double max = maximum.doubleValue();
        double mean = (min + max) / 2.0;
        double standardDeviation = Math.max((max - min) / 6.0, 0.0001);
        double u1 = Math.max(1.0e-12, random.nextDouble());
        double gaussian = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * random.nextDouble());
        return rate(BigDecimal.valueOf(Math.max(min, Math.min(max, mean + gaussian * standardDeviation))));
    }

    private static double tierMultiplier(String tier) {
        return switch (tier) {
            case "WEAK" -> 0.35;
            case "NORMAL" -> 0.75;
            default -> 1.10;
        };
    }

    private static double metadataMultiplier(GuessStockConfig.EventConfig event) {
        double relevance = switch (event.relevance()) { case "LOW" -> 0.70; case "HIGH" -> 1.20; default -> 1.0; };
        double credibility = switch (event.credibility()) { case "RUMOR" -> 0.70; case "PRELIMINARY" -> 0.90; default -> 1.05; };
        double surprise = switch (event.surprise()) { case "EXPECTED" -> 0.70; case "MAJOR" -> 1.25; default -> 1.0; };
        double strength = switch (event.strength()) { case "WEAK" -> 0.80; case "STRONG" -> 1.20; default -> 1.0; };
        return relevance * credibility * surprise * strength;
    }

    private static double regimeMultiplier(String regime, String direction) {
        boolean up = "UP".equals(direction);
        return switch (regime) {
            case "BULL" -> up ? 1.10 : 0.70;
            case "BEAR" -> up ? 0.70 : 1.10;
            case "OVERHEATED" -> up ? 0.55 : 1.25;
            case "OVERSOLD" -> up ? 1.25 : 0.55;
            case "HIGH_VOLATILITY" -> 0.90;
            default -> 0.90;
        };
    }

    private static double cohesionMultiplier(BigDecimal baseChangePercent, String direction) {
        if (baseChangePercent == null || baseChangePercent.signum() == 0) return 1.0;
        boolean sameDirection = (baseChangePercent.signum() > 0) == "UP".equals(direction);
        return sameDirection ? 1.05 : 0.85;
    }

    private static String reason(GuessStockConfig.EventConfig event, String regime, String direction,
                                 String tier, double factor) {
        String marketResponse = factor >= 1.0 ? "市场状态放大了定价反应" : "市场状态吸收了部分冲击";
        return typeLabel(event.eventType()) + "事件为" + credibilityLabel(event.credibility()) + "、"
                + surpriseLabel(event.surprise()) + "，当前处于" + regimeLabel(regime) + "；"
                + marketResponse + "，综合相关度与强度后形成" + tierLabel(tier) + "的"
                + ("UP".equals(direction) ? "正向" : "负向") + "影响。";
    }

    private static String typeLabel(String value) {
        return switch (value) { case "DEMAND" -> "需求"; case "SUPPLY" -> "供给"; case "POLICY" -> "政策";
            case "FINANCIAL" -> "财务"; case "ACCIDENT" -> "突发"; case "SENTIMENT" -> "情绪"; default -> "行业"; };
    }

    private static String credibilityLabel(String value) {
        return switch (value) { case "RUMOR" -> "传闻"; case "PRELIMINARY" -> "初步确认"; default -> "已确认"; };
    }

    private static String surpriseLabel(String value) {
        return switch (value) { case "EXPECTED" -> "市场已有预期"; case "MAJOR" -> "显著超预期"; default -> "一般意外"; };
    }

    private static String regimeLabel(String value) {
        return switch (value) { case "BULL" -> "上涨趋势"; case "BEAR" -> "下跌趋势";
            case "OVERHEATED" -> "过热状态"; case "OVERSOLD" -> "超卖状态";
            case "HIGH_VOLATILITY" -> "高波动状态"; default -> "震荡状态"; };
    }

    private static String tierLabel(String value) {
        return switch (value) { case "WEAK" -> "轻微"; case "NORMAL" -> "常规"; default -> "强烈"; };
    }

    private static BigDecimal rate(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
