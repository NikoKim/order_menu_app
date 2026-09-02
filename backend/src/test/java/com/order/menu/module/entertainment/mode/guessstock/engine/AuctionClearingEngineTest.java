package com.order.menu.module.entertainment.mode.guessstock.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuctionClearingEngineTest {
    @Test
    void clearingIsIndependentOfSubmissionOrderAndAccountsForFees() {
        var a = new AuctionClearingEngine.TargetOrder("A", new BigDecimal("10000"), 0, 80);
        var b = new AuctionClearingEngine.TargetOrder("B", new BigDecimal("2000"), 80, 20);
        var first = AuctionClearingEngine.clear(new BigDecimal("100"), List.of(a, b), 100,
                new BigDecimal("3"), new BigDecimal("0.05"), new BigDecimal("0.10"));
        var second = AuctionClearingEngine.clear(new BigDecimal("100"), List.of(b, a), 100,
                new BigDecimal("3"), new BigDecimal("0.05"), new BigDecimal("0.10"));
        assertEquals(first, second);
        first.fills().forEach(fill -> assertEquals(1, fill.feeAmount().signum()));
    }

    @Test
    void buyFillRemainsAffordableAtTheFinalClearingPrice() {
        var buyer = new AuctionClearingEngine.TargetOrder("BUYER", new BigDecimal("1000"), 0, 100);
        var result = AuctionClearingEngine.clear(new BigDecimal("100"), List.of(buyer), 10,
                new BigDecimal("10"), new BigDecimal("1"), new BigDecimal("1"));
        var fill = result.fills().get(0);
        assertTrue(fill.grossAmount().add(fill.feeAmount()).compareTo(new BigDecimal("1000")) <= 0);
    }

    @Test
    void proportionalAllocationNeverExceedsLiquidity() {
        var orders = List.of(
                new AuctionClearingEngine.TargetOrder("A", new BigDecimal("10000"), 0, 100),
                new AuctionClearingEngine.TargetOrder("B", new BigDecimal("10000"), 0, 100),
                new AuctionClearingEngine.TargetOrder("C", new BigDecimal("10000"), 0, 100));
        var result = AuctionClearingEngine.clear(new BigDecimal("100"), orders, 2,
                new BigDecimal("3"), BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals(2, result.fills().stream().mapToLong(AuctionClearingEngine.Fill::filledShares).sum());
    }
}
