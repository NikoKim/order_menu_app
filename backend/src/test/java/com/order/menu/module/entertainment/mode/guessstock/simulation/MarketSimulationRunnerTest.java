package com.order.menu.module.entertainment.mode.guessstock.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MarketSimulationRunnerTest {
    @Test
    void noEventNoFlowHasNearZeroMedianAndIsDeterministic() {
        var first = MarketSimulationRunner.run(2_000, 100, 3,
                MarketSimulationRunner.Scenario.NO_EVENT_NO_FLOW, 20260830L);
        var second = MarketSimulationRunner.run(2_000, 100, 3,
                MarketSimulationRunner.Scenario.NO_EVENT_NO_FLOW, 20260830L);

        assertEquals(first, second);
        assertTrue(Math.abs(first.medianReturnPct()) < 3);
        assertTrue(first.positiveGamePct() > 40 && first.positiveGamePct() < 60);
    }
}
