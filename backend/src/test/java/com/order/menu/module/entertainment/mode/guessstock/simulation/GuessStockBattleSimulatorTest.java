package com.order.menu.module.entertainment.mode.guessstock.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class GuessStockBattleSimulatorTest {
    @Test
    void pairedBattleIsDeterministicAndIncludesProductionBaselines() {
        var first = GuessStockBattleSimulator.run(200, 30, "NORMAL", 91L);
        var second = GuessStockBattleSimulator.run(200, 30, "NORMAL", 91L);
        assertEquals(first, second);
        assertEquals(7, first.strategies().size());
        assertEquals(0, first.strategies().get(GuessStockBattleSimulator.Strategy.CASH_ONLY).averageReturnPct());
        assertTrue(first.strategies().get(GuessStockBattleSimulator.Strategy.THESIS_AGENT).averageTrades() <= 20);
    }

    @Test
    void difficultyLeagueUsesFiveThousandPairedMarketSeeds() {
        var report = GuessStockBattleSimulator.runDifficultyLeague(5_000, 30, 20260831L);
        assertEquals(5_000, report.games());
        assertEquals(3, report.difficulties().size());
        assertTrue(report.normalOverEasyWinRatePct() >= 50, report.toString());
        assertTrue(report.hardOverNormalWinRatePct() >= 45, report.toString());
    }
}
