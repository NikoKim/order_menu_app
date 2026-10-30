package com.order.menu.module.entertainment.mode.guessstock.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Order-independent uniform-price target auction for the optional advanced trading mode. */
public final class AuctionClearingEngine {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private AuctionClearingEngine() {}

    public static Result clear(BigDecimal referencePrice, List<TargetOrder> orders, long liquidityShares,
                               BigDecimal maxOrderImpactPct, BigDecimal feePct, BigDecimal slippagePct) {
        return clear(referencePrice, orders, liquidityShares, maxOrderImpactPct, feePct, slippagePct, 0);
    }

    public static Result clear(BigDecimal referencePrice, List<TargetOrder> orders, long liquidityShares,
                               BigDecimal maxOrderImpactPct, BigDecimal feePct, BigDecimal slippagePct,
                               long forcedSellShares) {
        if (referencePrice == null || referencePrice.signum() <= 0) throw new IllegalArgumentException("invalid price");
        BigDecimal price = referencePrice;
        List<Intent> intents = List.of();
        long net = 0;
        double impact = 0;
        double slip = 0;
        for (int iteration = 0; iteration < 16; iteration++) {
            intents = intents(orders, price, feePct);
            long nextNet = intents.stream().mapToLong(item -> "BUY".equals(item.side()) ? item.requestedShares()
                    : -item.requestedShares()).sum() - Math.max(0, forcedSellShares);
            double imbalance = nextNet / (double) Math.max(1, liquidityShares);
            double nextImpact = clamp(imbalance * maxOrderImpactPct.doubleValue(),
                    -maxOrderImpactPct.doubleValue(), maxOrderImpactPct.doubleValue());
            double nextSlip = Math.signum(imbalance)
                    * Math.min(slippagePct.doubleValue(), Math.abs(nextImpact) * .25);
            BigDecimal nextPrice = referencePrice.multiply(BigDecimal.valueOf(
                            Math.exp((nextImpact + nextSlip) / 100d)))
                    .setScale(4, RoundingMode.HALF_UP);
            boolean stable = nextNet == net && nextPrice.compareTo(price) == 0;
            net = nextNet;
            impact = nextImpact;
            slip = nextSlip;
            price = nextPrice;
            if (stable) break;
        }
        // Recalculate at the final clearing price so a filled buy can never exceed cash plus fees.
        intents = intents(orders, price, feePct);
        long totalRequested = intents.stream().mapToLong(Intent::requestedShares).sum();
        long availableForOrders = Math.max(0, liquidityShares - Math.max(0, forcedSellShares));
        double fillRatio = Math.min(1, availableForOrders / (double) Math.max(1, totalRequested));
        long[] allocations = proportionalAllocations(intents, availableForOrders, fillRatio);
        List<Fill> fills = new ArrayList<>();
        for (int index = 0; index < intents.size(); index++) {
            Intent intent = intents.get(index);
            long filled = allocations[index];
            BigDecimal gross = price.multiply(BigDecimal.valueOf(filled)).setScale(4, RoundingMode.HALF_UP);
            BigDecimal fee = gross.multiply(feePct).divide(HUNDRED, 8, RoundingMode.HALF_UP)
                    .setScale(4, RoundingMode.HALF_UP);
            fills.add(new Fill(intent.participantKey(), intent.side(), intent.requestedShares(), filled,
                    price, gross, fee, BigDecimal.valueOf(slip).setScale(4, RoundingMode.HALF_UP)));
        }
        return new Result(price, BigDecimal.valueOf(impact).setScale(4, RoundingMode.HALF_UP), net,
                fillRatio, List.copyOf(fills));
    }

    private static List<Intent> intents(List<TargetOrder> orders, BigDecimal price, BigDecimal feePct) {
        return orders.stream().map(order -> intent(order, price, feePct))
                .sorted(Comparator.comparing(Intent::participantKey)).toList();
    }

    private static long[] proportionalAllocations(List<Intent> intents, long available, double ratio) {
        long[] values = new long[intents.size()];
        List<Integer> residualOrder = new ArrayList<>();
        long used = 0;
        for (int index = 0; index < intents.size(); index++) {
            double exact = intents.get(index).requestedShares() * ratio;
            values[index] = Math.min(intents.get(index).requestedShares(), (long) Math.floor(exact));
            used += values[index];
            residualOrder.add(index);
        }
        residualOrder.sort(Comparator.<Integer>comparingDouble(index ->
                -(intents.get(index).requestedShares() * ratio - values[index]))
                .thenComparing(index -> intents.get(index).participantKey()));
        long remaining = Math.max(0, Math.min(available, intents.stream().mapToLong(Intent::requestedShares).sum()) - used);
        for (int index : residualOrder) {
            if (remaining == 0) break;
            if (values[index] < intents.get(index).requestedShares()) { values[index]++; remaining--; }
        }
        return values;
    }

    private static Intent intent(TargetOrder order, BigDecimal price, BigDecimal feePct) {
        BigDecimal assets = order.cash().add(price.multiply(BigDecimal.valueOf(order.currentShares())))
                .subtract(order.debt() == null ? BigDecimal.ZERO : order.debt()).max(BigDecimal.ZERO);
        long targetShares = assets.multiply(BigDecimal.valueOf(clamp(order.targetExposurePct(), 0, 300)))
                .divide(HUNDRED, 10, RoundingMode.HALF_UP).divide(price, 0, RoundingMode.FLOOR).longValue();
        long delta = targetShares - order.currentShares();
        BigDecimal buyUnitCost = price.multiply(BigDecimal.ONE.add(feePct.divide(HUNDRED, 10, RoundingMode.HALF_UP)));
        long requested = delta >= 0 ? Math.min(delta, order.cash().divide(buyUnitCost, 0, RoundingMode.FLOOR).longValue())
                : Math.min(-delta, order.currentShares());
        String side = delta >= 0 ? "BUY" : "SELL";
        if ((order.allowedSide() != null && !order.allowedSide().equals(side))
                || (order.minPrice() != null && price.compareTo(order.minPrice()) < 0)
                || (order.maxPrice() != null && price.compareTo(order.maxPrice()) > 0)) requested = 0;
        if (order.maxShares() != null) requested = Math.min(requested, Math.max(0, order.maxShares()));
        if (order.maxNotional() != null) requested = Math.min(requested,
                order.maxNotional().max(BigDecimal.ZERO).divide(price, 0, RoundingMode.FLOOR).longValue());
        if (order.minNotional() != null && price.multiply(BigDecimal.valueOf(Math.max(0, requested)))
                .compareTo(order.minNotional()) < 0) requested = 0;
        return new Intent(order.participantKey(), side, Math.max(0, requested));
    }

    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    public record TargetOrder(String participantKey, BigDecimal cash, long currentShares,
                              double targetExposurePct, BigDecimal debt, String allowedSide, Long maxShares,
                              BigDecimal maxNotional, BigDecimal minNotional,
                              BigDecimal minPrice, BigDecimal maxPrice) {
        public TargetOrder(String participantKey, BigDecimal cash, long currentShares,
                           double targetExposurePct, BigDecimal debt) {
            this(participantKey, cash, currentShares, targetExposurePct, debt, null, null, null, null, null, null);
        }
        public TargetOrder(String participantKey, BigDecimal cash, long currentShares, double targetExposurePct) {
            this(participantKey, cash, currentShares, targetExposurePct, BigDecimal.ZERO,
                    null, null, null, null, null, null);
        }
    }
    private record Intent(String participantKey, String side, long requestedShares) {}
    public record Fill(String participantKey, String side, long requestedShares, long filledShares,
                       BigDecimal executionPrice, BigDecimal grossAmount, BigDecimal feeAmount,
                       BigDecimal slippagePercent) {}
    public record Result(BigDecimal clearingPrice, BigDecimal orderImpactPercent, long netRequestedShares,
                         double fillRatio, List<Fill> fills) {}
}
