package com.order.menu.module.entertainment.mode.guessstock.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AiBattleSimulationRunnerTest {
    @Test
    void deterministicBattleReportTracksTurnoverAndTrades() {
        var first = AiBattleSimulationRunner.run(200, 100, "NORMAL", 20260830L);
        var second = AiBattleSimulationRunner.run(200, 100, "NORMAL", 20260830L);
        assertEquals(first, second);
        assertTrue(first.averageTradeCount() >= 0);
        assertTrue(first.averageTurnoverPct() >= 0);
        assertTrue(first.averageDecisionChecks() > 0 && first.averageDecisionChecks() < 100);
    }
}
