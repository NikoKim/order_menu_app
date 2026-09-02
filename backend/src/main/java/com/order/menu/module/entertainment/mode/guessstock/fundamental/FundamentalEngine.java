package com.order.menu.module.entertainment.mode.guessstock.fundamental;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Deterministic company fundamentals used only by pricing V3. Indices are bounded to 0..200. */
public final class FundamentalEngine {
    private FundamentalEngine() {}

    public static State neutral() { return new State(100, 100, 100, 100, 100); }

    public static State applyEvent(State state, String eventType, String direction, String strength) {
        State current = state == null ? neutral() : state;
        double sign = "DOWN".equalsIgnoreCase(direction) ? -1 : "UP".equalsIgnoreCase(direction) ? 1 : 0;
        double size = switch (strength == null ? "" : strength.toUpperCase()) {
            case "STRONG" -> 8; case "WEAK" -> 2.5; default -> 5;
        } * sign;
        double demand = 0, orders = 0, inventory = 0, margin = 0, cash = 0;
        switch (eventType == null ? "INDUSTRY" : eventType.toUpperCase()) {
            case "DEMAND" -> { demand = size; orders = size * .75; inventory = -size * .35; }
            case "SUPPLY" -> { inventory = -size * .7; margin = size * .55; cash = size * .2; }
            case "FINANCIAL" -> { margin = size * .75; cash = size; }
            case "ACCIDENT" -> { inventory = size * .55; margin = size * .65; cash = size * .5; }
            case "POLICY" -> { demand = size * .35; margin = size * .55; cash = size * .35; }
            default -> { demand = size * .3; orders = size * .3; margin = size * .25; }
        }
        return new State(bound(current.demandIndex() + demand), bound(current.orderIndex() + orders),
                bound(current.inventoryIndex() + inventory), bound(current.marginIndex() + margin),
                bound(current.cashFlowIndex() + cash));
    }

    public static Metrics metrics(State state) {
        State value = state == null ? neutral() : state;
        double inventoryHealth = 200 - value.inventoryIndex();
        double score = .25 * value.demandIndex() + .25 * value.orderIndex() + .15 * inventoryHealth
                + .20 * value.marginIndex() + .15 * value.cashFlowIndex();
        double fair = 100 + (score - 100) * .75;
        double risk = bound(100 - (.55 * value.cashFlowIndex() + .45 * value.marginIndex() - 100));
        return new Metrics(rate(score), rate(fair), rate(risk));
    }

    public static PriceContribution priceContribution(State state, BigDecimal initialPrice, BigDecimal currentPrice,
                                                       BigDecimal sensitivity, boolean reportRound) {
        Metrics metrics = metrics(state);
        double fairPrice = initialPrice.doubleValue() * metrics.fairValueIndex().doubleValue() / 100d;
        double gap = currentPrice.signum() <= 0 ? 0 : Math.log(fairPrice / currentPrice.doubleValue()) * 100;
        double factor = sensitivity == null ? .35 : sensitivity.doubleValue();
        double anchor = clamp(gap * .03 * factor, -.60, .60);
        double surprise = reportRound ? clamp((metrics.fundamentalScore().doubleValue() - 100) * .025 * factor,
                -.80, .80) : 0;
        return new PriceContribution(rate(anchor), rate(surprise));
    }

    private static double bound(double value) { return clamp(value, 0, 200); }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private static BigDecimal rate(double value) { return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP); }

    public record State(double demandIndex, double orderIndex, double inventoryIndex,
                        double marginIndex, double cashFlowIndex) {}
    public record Metrics(BigDecimal fundamentalScore, BigDecimal fairValueIndex, BigDecimal financialRisk) {}
    public record PriceContribution(BigDecimal fundamentalImpactPct, BigDecimal reportSurprisePct) {}
}
