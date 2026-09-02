package com.order.menu.module.entertainment.mode.guessstock.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AiTargetPositionExecutorTest {
    private final AiTargetPositionExecutor executor = new AiTargetPositionExecutor();
    private final AiTargetPositionExecutor.Guard guard = new AiTargetPositionExecutor.Guard(
            bd("70"), bd("5"), 1, bd("2"), bd("30"), 10_000, bd("2"));

    @Test
    void fivePointBandPreventsSmallTargetOscillation() {
        assertEquals("HOLD", executor.plan(guard, context(38, "NONE", false, bd("0"))).direction());
        assertEquals("SELL", executor.plan(guard, context(34, "NONE", false, bd("0"))).direction());
    }

    @Test
    void sameRoundReverseIsBlockedUnlessThesisWasInvalidated() {
        AiTargetPositionExecutor.ExecutionPlan blocked = executor.plan(guard,
                context(20, "BUY", false, bd("0")));
        AiTargetPositionExecutor.ExecutionPlan escaped = executor.plan(guard,
                context(20, "BUY", true, bd("0")));

        assertEquals("HOLD", blocked.direction());
        assertTrue(blocked.reason().contains("反向"));
        assertEquals("SELL", escaped.direction());
    }

    @Test
    void turnoverLimitStopsFurtherOrders() {
        AiTargetPositionExecutor.ExecutionPlan plan = executor.plan(guard,
                context(20, "NONE", false, bd("3000")));
        assertEquals("HOLD", plan.direction());
        assertTrue(plan.reason().contains("换手"));
    }

    @Test
    void normalSignalMovesAtMostFifteenPositionPoints() {
        AiTargetPositionExecutor.ExecutionPlan plan = executor.plan(guard,
                new AiTargetPositionExecutor.Context("OPENING", 6, 70, 20, 200,
                        bd("8000"), bd("10000"), bd("10000"), bd("0"), bd("10"), "MEDIUM",
                        null, "NONE", false, false, bd("1000"), 5, bd("10"),
                        null, null, false));
        assertEquals(35, plan.targetPositionPct());
        assertEquals(70, plan.requestedTargetPositionPct());
    }

    @Test
    void crossRoundReverseIsBlockedDuringCooldown() {
        AiTargetPositionExecutor.ExecutionPlan plan = executor.plan(guard,
                new AiTargetPositionExecutor.Context("CLOSING", 5, 10, 40, 400,
                        bd("6000"), bd("10000"), bd("10000"), bd("0"), bd("10"), "MEDIUM",
                        2, "NONE", false, false, bd("1000"), 5, bd("10"),
                        4, "BUY", false));
        assertEquals("HOLD", plan.direction());
        assertTrue(plan.reason().contains("冷却"));
    }

    @Test
    void leveragedExposureIsNotCappedAtOneHundredPercent() {
        AiTargetPositionExecutor.Guard leveraged = new AiTargetPositionExecutor.Guard(
                bd("250"), bd("3"), 0, bd("1"), bd("300"), 10_000, bd("10"),
                bd("15"), bd("30"), 0);
        AiTargetPositionExecutor.ExecutionPlan plan = executor.plan(leveraged,
                new AiTargetPositionExecutor.Context("OPENING", 6, 230, 100, 1000,
                        bd("20000"), bd("10000"), bd("10000"), bd("0"), bd("10"), "HIGH",
                        null, "NONE", false, false, bd("10000"), 5, bd("10"),
                        null, null, false));

        assertEquals("BUY", plan.direction());
        assertEquals(130, plan.targetPositionPct());
    }

    @Test
    void deliberateDeleveragingCanMoveTwiceAsFast() {
        AiTargetPositionExecutor.Guard leveraged = new AiTargetPositionExecutor.Guard(
                bd("250"), bd("3"), 0, bd("1"), bd("300"), 10_000, bd("10"),
                bd("15"), bd("30"), 0);
        AiTargetPositionExecutor.ExecutionPlan plan = executor.plan(leveraged,
                new AiTargetPositionExecutor.Context("CLOSING", 6, 100, 180, 1800,
                        bd("0"), bd("10000"), bd("10000"), bd("0"), bd("10"), "MEDIUM",
                        null, "NONE", false, false, bd("10000"), 5, bd("10"),
                        null, null, false));

        assertEquals("SELL", plan.direction());
        assertEquals(150, plan.targetPositionPct());
    }

    private AiTargetPositionExecutor.Context context(double target, String direction,
                                                      boolean invalidated, BigDecimal turnover) {
        return new AiTargetPositionExecutor.Context("CLOSING", 4, target, 40, 400,
                bd("6000"), bd("10000"), bd("10000"), turnover, bd("10"), "MEDIUM",
                2, direction, invalidated, false, bd("1000"), 5, bd("10"));
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
