package com.order.menu.module.entertainment.mode.guessstock.simulation;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class AiStrategyArenaSimulatorTest {
    @Test
    void eightAiLongGamesRemainSolventAndExerciseMoreThanOneStrategyPhase() throws Exception {
        int games = Boolean.getBoolean("ai.arena.full") ? 200 : 8;
        long seed = Long.getLong("ai.arena.seed", 20261022L);
        var reports = new ArrayList<AiStrategyArenaSimulator.Report>();
        for (var baseline : AiStrategyArenaSimulator.Baseline.values()) {
            var report = AiStrategyArenaSimulator.run(games, 180, "HARD", baseline, seed);
            reports.add(report);
            assertTrue(Double.isFinite(report.averageAiReturnPct()));
            assertTrue(report.averageAiDrawdownPct() > -100);
            assertTrue(report.averageAiTrades() > 8);
            assertTrue(report.probes() > 0);
            assertTrue(report.exits() > 0);
        }
        var league = AiStrategyArenaSimulator.difficultyLeague(games, 180, AiStrategyArenaSimulator.Baseline.TREND, seed);
        assertTrue(league.hardMeanReturnPct() > league.normalMeanReturnPct());
        if (games >= 100) assertTrue(league.hardOverNormalPct() >= 55, "Hard must win at least 55% of paired risk-adjusted team scores");
        Path output = Path.of(System.getProperty("ai.arena.report", "target/guess-stock-ai-arena/report.json"));
        Files.createDirectories(output.getParent());
        Files.writeString(output, new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(
                java.util.Map.of("seed", seed, "baselines", reports, "difficultyLeague", league,
                        "scope", "Local tactical agents; no paid model calls; no credit ledger; not a human win-rate claim")));
        System.out.println("AI_ARENA " + new ObjectMapper().writeValueAsString(reports));
        System.out.println("AI_DIFFICULTY_LEAGUE " + new ObjectMapper().writeValueAsString(league));
    }

    @Test
    void identicalSeedsProduceIdenticalResults() {
        assertEquals(AiStrategyArenaSimulator.run(2, 30, "HARD", AiStrategyArenaSimulator.Baseline.TREND, 44),
                AiStrategyArenaSimulator.run(2, 30, "HARD", AiStrategyArenaSimulator.Baseline.TREND, 44));
    }

    @Test
    void repeatedSyntheticNeutralZeroReviewsCannotFreezeTheWholeLineup() throws Exception {
        var report = AiStrategyArenaSimulator.runWithNeutralModelObservations(8, 180, "HARD",
                AiStrategyArenaSimulator.Baseline.TREND, 314159265L);
        assertTrue(report.averageAiTrades() > 100);
        assertTrue(report.probes() > 0);
        assertTrue(report.accumulations() > 0);
        assertTrue(report.exits() > 0);
        Path output = Path.of("target/guess-stock-ai-arena/neutral-zero-stress.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report));
    }
}
