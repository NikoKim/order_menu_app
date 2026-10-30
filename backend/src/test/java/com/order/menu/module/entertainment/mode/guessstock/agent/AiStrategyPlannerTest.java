package com.order.menu.module.entertainment.mode.guessstock.agent;

import static org.junit.jupiter.api.Assertions.*;
import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockRound;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiStrategyPlannerTest {

    @Test
    void contrarianProbesThenConfirmsInsteadOfBuyingTheWholeFallingMarket() {
        var first = AiStrategyPlanner.evaluate(context(8, "CONTRARIAN", 0, 0, 0, "SAFE", 0, .02,
                100, 100, 100, 99, 96, 91, 90, 90.5), null);
        assertEquals("PROBE", first.state().phase());
        assertTrue(first.target() > 0 && first.target() <= 25);
        var second = AiStrategyPlanner.evaluate(context(9, "CONTRARIAN", 10, 10, 90.5, "SAFE", 0, .02,
                100, 100, 100, 99, 96, 91, 90, 90.5, 91), first.state());
        assertEquals("ACCUMULATE", second.state().phase());
        assertTrue(second.target() > first.target());
    }

    @Test
    void acceleratingCrashDoesNotBecomeAnUnlimitedAveragingStrategy() {
        var plan = AiStrategyPlanner.evaluate(context(8, "CONTRARIAN", 0, 0, 0, "SAFE", 0, .02,
                100, 100, 100, 99, 98, 96, 92, 85), null);
        assertEquals(0, plan.target());
        assertEquals("FALLING_KNIFE_NO_AVERAGING", plan.reasonCode());
    }

    @Test
    void creditWarningAndMaturityActWithoutCallingAModel() {
        var warning = AiStrategyPlanner.evaluate(context(8, "AGGRESSIVE", 180, 150, 100, "WARNING", 20, .02,
                100, 101, 102, 103, 104, 105, 106, 107), null);
        assertTrue(warning.risk());
        assertTrue(warning.target() <= 60);
        var due = AiStrategyPlanner.evaluate(context(8, "AGGRESSIVE", 180, 150, 100, "SAFE", 9, .02,
                100, 101, 102, 103, 104, 105, 106, 107), null);
        assertEquals("MATURITY_DELEVERAGE", due.reasonCode());
    }

    @Test
    void trailingProtectionExitsBeforeWaitingForPeriodicModelReview() {
        var state = new AiStrategyPlanner.State("RIDE", 2, 2, 120, 115, 90, 110, 7, 100, 0, 0, 90);
        var plan = AiStrategyPlanner.evaluate(context(8, "TREND", 90, 90, 95, "SAFE", 0, .02,
                100, 103, 108, 115, 120, 119, 117, 112), state);
        assertEquals("TRAILING_STOP", plan.reasonCode());
        assertEquals(0, plan.target());
    }

    @Test
    void feesCanMakeAnOtherwiseValidEntryUnprofitable() {
        var plan = AiStrategyPlanner.evaluate(context(8, "TREND", 0, 0, 0, "SAFE", 0, 5,
                100, 100.1, 100.2, 100.3, 100.4, 100.5, 100.6, 100.7), null);
        assertEquals("COST_EXCEEDS_EDGE", plan.reasonCode());
        assertEquals(0, plan.target());
    }

    @Test
    void newResearchChangesTheCurrentValuationWithoutAnotherModelRequest() {
        var market = context(8, "CONTRARIAN", 0, 0, 0, "SAFE", 0, .02,
                100, 99, 98, 95, 92, 90, 90.5, 91);
        var researched = new AiStrategyPlanner.Context(market.market(), market.style(), market.difficulty(),
                0, 100, 0, 0, 10000, 60, 7, 12, 0, "SAFE", 0,
                .02, .02, 0, 10000, 3, 0);
        var plan = AiStrategyPlanner.evaluate(researched, null);
        assertTrue(plan.target() <= 10);
        assertTrue(plan.state().fairHigh() < 90);
    }

    @Test
    void aFreshResearchResultLateInALongGameIsNotTreatedAsAnAncientReport() {
        var market = context(178, "CONTRARIAN", 0, 0, 0, "SAFE", 0, .02,
                100, 99, 98, 95, 92, 90, 90.5, 91);
        var researched = new AiStrategyPlanner.Context(market.market(), "CONTRARIAN", "HARD", 0, 100,
                0, 0, 10000, 60, 0, 12, 0, "SAFE", 0, .02, .02, 0, 10000, 3, 0, 0, 178);
        assertTrue(AiStrategyPlanner.evaluate(researched, null).state().fairHigh() < 90);
    }

    @Test
    void aNewPositionDoesNotInheritAnUnownedPricePeakOrStop() {
        var old = new AiStrategyPlanner.State("PROBE", 7, 0, 100, 96, 90, 105, 1, 100, 0, 0, 15);
        var plan = AiStrategyPlanner.evaluate(context(8, "CONTRARIAN", 15, 15, 85, "SAFE", 0, .02,
                85, 85, 85, 85, 85, 85, 85, 85), old);
        assertFalse(plan.risk());
        assertEquals(85, plan.state().peakPrice());
        assertTrue(plan.state().stopPrice() < 85);
    }

    @Test
    void anExitContinuesAfterTheOriginalRiskDisappearsUntilAllSharesAreSold() {
        var old = new AiStrategyPlanner.State("EXIT", 7, 2, 100, 90, 90, 105, 1, 100, 0, 0, 0);
        var plan = AiStrategyPlanner.evaluate(context(8, "CONTRARIAN", 15, 15, 95, "SAFE", 0, .02,
                96, 96, 96, 96, 96, 96, 96, 96), old);
        assertTrue(plan.risk());
        assertEquals(0, plan.target());
        assertEquals("EXIT_PLAN_IN_PROGRESS", plan.reasonCode());
    }

    @Test
    void tacticalAdjustmentsRetainTheModelsCoreReason() {
        var market = context(8, "TREND", 0, 0, 0, "SAFE", 0, .02, 100, 101, 102, 103, 104, 105, 106, 107);
        var old = AiStrategyPlanner.State.empty(100).withModel(1, 80, "confirmed public demand growth");
        assertEquals("confirmed public demand growth", AiStrategyPlanner.evaluate(market, old).state().modelReason());
    }

    @Test
    void neutralZeroTargetDoesNotPermanentlyDisableNewPublicOpportunities() {
        var market = context(8, "TREND", 0, 0, 0, "SAFE", 0, .02,
                100, 101, 102, 103, 104, 105, 106, 107);
        var fresh = AiStrategyPlanner.State.empty(100).withModel(8, 0, "observe", false);
        assertEquals(0, AiStrategyPlanner.evaluate(market, fresh).target());
        var prior = AiStrategyPlanner.State.empty(100).withModel(1, 0, "observe", false);
        assertTrue(AiStrategyPlanner.evaluate(market, prior).target() > 0);
        for (String style : List.of("CONSERVATIVE", "TREND", "CONTRARIAN", "AGGRESSIVE", "RANDOM")) {
            assertTrue(AiStrategyPlanner.evaluate(context(8, style, 0, 0, 0, "SAFE", 0, .02,
                    100, 101, 102, 103, 104, 105, 106, 107), prior).target() > 0, style);
        }
        var bottom = context(8, "CONTRARIAN", 0, 0, 0, "SAFE", 0, .02,
                100, 100, 100, 99, 96, 91, 90, 90.5);
        assertEquals("PROBE", AiStrategyPlanner.evaluate(bottom, prior).state().phase());
        assertTrue(AiStrategyPlanner.evaluate(bottom, prior).target() > 0);
        var noEdge = context(8, "TREND", 0, 0, 0, "SAFE", 0, .02,
                100, 100, 100, 100, 100, 100, 100, 100);
        assertEquals(0, AiStrategyPlanner.evaluate(noEdge, prior).target());
    }

    @Test
    void explicitBearishExitAndPositiveModelCapsRemainBinding() {
        var market = context(8, "TREND", 0, 0, 0, "SAFE", 0, .02,
                100, 101, 102, 103, 104, 105, 106, 107);
        var blocked = AiStrategyPlanner.State.empty(100).withModel(1, 0, "no long entry", true);
        assertEquals(0, AiStrategyPlanner.evaluate(market, blocked).target());
        assertTrue(AiStrategyPlanner.evaluate(market, blocked).state().modelEntryBlocked());
        assertTrue(blocked.withModel(8, 0).modelEntryBlocked());
        assertTrue(AiStrategyPlanner.evaluate(market,
                AiStrategyPlanner.State.empty(100).withModel(1, 20, "small budget", false)).target() <= 20);
    }

    @Test
    void explicitEntryBlockSurvivesPersistentStrategyStateRoundTrips() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var blocked = AiStrategyPlanner.State.empty(100).withModel(1, 0, "bearish exit", true);
        assertEquals(blocked, mapper.readValue(mapper.writeValueAsString(blocked), AiStrategyPlanner.State.class));
    }

    @Test
    void aShortPullbackDoesNotInvalidateAnIntactLongerTrend() {
        var plan = AiStrategyPlanner.evaluate(context(8, "TREND", 80, 80, 100, "SAFE", 0, .02,
                100, 102, 104, 108, 115, 112, 110, 108), null);
        assertFalse(plan.risk());
        assertTrue(plan.target() > 0);
    }

    @Test
    void strongConfirmedTrendUsesTheExistingStrongExecutionStepRatherThanMoreModelCalls() {
        var plan = AiStrategyPlanner.evaluate(context(8, "TREND", 0, 0, 0, "SAFE", 0, .02,
                100, 101, 102, 103, 104, 105, 106, 107), null);
        assertEquals("HIGH", plan.urgency());
        assertEquals("RIDE", plan.state().phase());
    }

    static AiStrategyPlanner.Context context(int round, String style, double exposure, long shares,
            double cost, String creditRisk, int maturity, double fee, double... prices) {
        List<GuessStockRound> history = new ArrayList<>();
        for (int i = 0; i < prices.length - 1; i++) {
            GuessStockRound point = new GuessStockRound();
            point.setClosePrice(BigDecimal.valueOf(prices[i]));
            point.setRoundNo(i + 1);
            history.add(point);
        }
        GuessStockRound current = new GuessStockRound();
        current.setOpeningEventOccurred(0);
        current.setClosingEventOccurred(0);
        var snapshot = new AiMarketSnapshotService().create(round, 180, "OPENING",
                BigDecimal.valueOf(prices[prices.length - 1]), BigDecimal.valueOf(prices[prices.length - 2]),
                history, current, 0, 0, 3);
        return new AiStrategyPlanner.Context(snapshot, style, "HARD", exposure, 200, shares, cost, 10000,
                0, 0, 0, exposure > 100 ? 5000 : 0, creditRisk, maturity, fee, .02, .3, 10000, 3, 0);
    }
}
