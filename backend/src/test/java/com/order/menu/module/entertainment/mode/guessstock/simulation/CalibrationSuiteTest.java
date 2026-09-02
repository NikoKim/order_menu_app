package com.order.menu.module.entertainment.mode.guessstock.simulation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CalibrationSuiteTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void v2DistributionStaysInsideFixedRegressionBaseline() throws Exception {
        JsonNode baseline = baseline();
        var noEvent = MarketSimulationRunner.runV2(1_000, 100, 3,
                MarketSimulationRunner.Scenario.NO_EVENT_NO_FLOW, 41L);
        var balanced = MarketSimulationRunner.runV2(1_000, 100, 3,
                MarketSimulationRunner.Scenario.BALANCED_EVENTS_NO_FLOW, 42L);

        assertTrue(Math.abs(noEvent.medianReturnPct()) <= baseline.path("noEventMedianAbsMaxPct").asDouble());
        assertTrue(noEvent.positiveGamePct() >= baseline.path("positiveGamePctMin").asDouble());
        assertTrue(noEvent.positiveGamePct() <= baseline.path("positiveGamePctMax").asDouble());
        assertTrue(Math.abs(balanced.medianReturnPct()) <= baseline.path("balancedEventMedianAbsMaxPct").asDouble());
        assertTrue(noEvent.p99AbsoluteStageMovePct() <= baseline.path("ordinaryStageMoveP99MaxPct").asDouble());
    }

    @Test
    void writeFullCalibrationReportWhenRequested() throws Exception {
        if (!Boolean.getBoolean("calibration.full")) return;
        List<MarketSimulationRunner.Report> marketV1 = new ArrayList<>();
        List<MarketSimulationRunner.Report> marketV2 = new ArrayList<>();
        for (MarketSimulationRunner.Scenario scenario : MarketSimulationRunner.Scenario.values()) {
            marketV1.add(MarketSimulationRunner.run(10_000, 100, 3, scenario,
                    20260830L + scenario.ordinal()));
            marketV2.add(MarketSimulationRunner.runV2(10_000, 100, 3, scenario,
                    20260830L + scenario.ordinal()));
        }
        List<AiBattleSimulationRunner.Report> ai = List.of(
                AiBattleSimulationRunner.run(1_000, 100, "EASY", 11L),
                AiBattleSimulationRunner.run(1_000, 100, "NORMAL", 12L),
                AiBattleSimulationRunner.run(1_000, 100, "HARD", 13L));
        GuessStockBattleSimulator.DifficultyLeagueReport difficultyLeague =
                GuessStockBattleSimulator.runDifficultyLeague(5_000, 30, 20260831L);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("marketV1", marketV1);
        report.put("marketV2", marketV2);
        report.put("ai", ai);
        report.put("difficultyLeague", difficultyLeague);
        Path directory = Path.of("target", "guess-stock-calibration");
        Files.createDirectories(directory);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("report.json").toFile(), report);
        Files.writeString(directory.resolve("market-v1.csv"), marketCsv(marketV1), StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("market-v2.csv"), marketCsv(marketV2), StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("market.csv"), marketCsv(marketV2), StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("ai.csv"), aiCsv(ai), StandardCharsets.UTF_8);
        validateFullReport(marketV2, ai, baseline());
    }

    private void validateFullReport(List<MarketSimulationRunner.Report> market,
                                    List<AiBattleSimulationRunner.Report> ai, JsonNode baseline) {
        MarketSimulationRunner.Report noEvent = market.stream()
                .filter(item -> item.scenario() == MarketSimulationRunner.Scenario.NO_EVENT_NO_FLOW).findFirst().orElseThrow();
        MarketSimulationRunner.Report balanced = market.stream()
                .filter(item -> item.scenario() == MarketSimulationRunner.Scenario.BALANCED_EVENTS_NO_FLOW).findFirst().orElseThrow();
        MarketSimulationRunner.Report full = market.stream()
                .filter(item -> item.scenario() == MarketSimulationRunner.Scenario.FULL_MARKET).findFirst().orElseThrow();
        assertTrue(Math.abs(noEvent.medianReturnPct()) <= baseline.path("noEventMedianAbsMaxPct").asDouble());
        assertTrue(Math.abs(balanced.medianReturnPct()) <= baseline.path("balancedEventMedianAbsMaxPct").asDouble());
        assertTrue(balanced.positiveGamePct() >= baseline.path("positiveGamePctMin").asDouble());
        assertTrue(balanced.positiveGamePct() <= baseline.path("positiveGamePctMax").asDouble());
        assertTrue(full.p99AbsoluteStageMovePct() <= baseline.path("eventStageMoveP99MaxPct").asDouble());
        assertTrue(full.averageRegimeSwitches() >= baseline.path("regimeSwitchesMinPer100").asDouble());
        assertTrue(full.averageRegimeSwitches() <= baseline.path("regimeSwitchesMaxPer100").asDouble());
        assertTrue(full.lowVolatilityRoundPct() >= 15 && full.lowVolatilityRoundPct() <= 35);
        assertTrue(full.normalVolatilityRoundPct() >= 40 && full.normalVolatilityRoundPct() <= 70);
        assertTrue(full.highVolatilityRoundPct() >= 8 && full.highVolatilityRoundPct() <= 25);
        assertTrue(full.extremeVolatilityRoundPct() >= .5 && full.extremeVolatilityRoundPct() <= 6);
        assertTrue(ai.stream().allMatch(item -> item.averageTradeCount()
                <= baseline.path("aiTradeCountMaxPer100").asDouble()));
    }

    private JsonNode baseline() throws Exception {
        try (var input = getClass().getResourceAsStream("/guess-stock-calibration-baseline.json")) {
            return objectMapper.readTree(input);
        }
    }

    private static String marketCsv(List<MarketSimulationRunner.Report> values) {
        StringBuilder csv = new StringBuilder("scenario,engine,games,medianReturn,positiveGamePct,medianDrawdown,largestMove,p99StageMove,realizedVolatility,regimeSwitches,eventCount,longestUp,longestDown,strongUpPct,upPct,sidewaysPct,downPct,strongDownPct,lowVolPct,normalVolPct,highVolPct,extremeVolPct,volSwitches,base,event,flow\n");
        for (var value : values) csv.append(value.scenario()).append(',').append(value.engineVersion()).append(',')
                .append(value.games()).append(',').append(value.medianReturnPct()).append(',')
                .append(value.positiveGamePct()).append(',').append(value.medianMaxDrawdownPct()).append(',')
                .append(value.averageLargestMovePct()).append(',').append(value.p99AbsoluteStageMovePct()).append(',')
                .append(value.averageRealizedVolatilityPct()).append(',').append(value.averageRegimeSwitches()).append(',')
                .append(value.averageEventCount()).append(',').append(value.averageLongestUpStreak()).append(',')
                .append(value.averageLongestDownStreak()).append(',').append(value.strongUpRoundPct()).append(',')
                .append(value.upRoundPct()).append(',').append(value.sidewaysRoundPct()).append(',')
                .append(value.downRoundPct()).append(',').append(value.strongDownRoundPct()).append(',')
                .append(value.lowVolatilityRoundPct()).append(',').append(value.normalVolatilityRoundPct()).append(',')
                .append(value.highVolatilityRoundPct()).append(',').append(value.extremeVolatilityRoundPct()).append(',')
                .append(value.averageVolatilitySwitches()).append(',')
                .append(value.averageBaseContributionPct()).append(',').append(value.averageEventContributionPct())
                .append(',').append(value.averageFlowContributionPct()).append('\n');
        return csv.toString();
    }

    private static String aiCsv(List<AiBattleSimulationRunner.Report> values) {
        StringBuilder csv = new StringBuilder("difficulty,games,averageReturn,medianReturn,sharpeLike,drawdown,winRate,trades,turnover,holdingRatio,averageHoldingRounds,reverseTrades,checks\n");
        for (var value : values) csv.append(value.difficulty()).append(',').append(value.games()).append(',')
                .append(value.averageReturnPct()).append(',').append(value.medianReturnPct()).append(',')
                .append(value.sharpeLike()).append(',').append(value.averageMaxDrawdownPct()).append(',')
                .append(value.winRatePct()).append(',')
                .append(value.averageTradeCount()).append(',').append(value.averageTurnoverPct()).append(',')
                .append(value.averageHoldingRoundRatio()).append(',').append(value.averageHoldingRounds()).append(',')
                .append(value.averageReverseTrades()).append(',')
                .append(value.averageDecisionChecks()).append('\n');
        return csv.toString();
    }
}
