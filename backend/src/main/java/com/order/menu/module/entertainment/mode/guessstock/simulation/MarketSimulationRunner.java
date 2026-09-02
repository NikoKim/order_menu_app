package com.order.menu.module.entertainment.mode.guessstock.simulation;

import com.order.menu.module.entertainment.mode.guessstock.engine.MarketStateCalculator;
import com.order.menu.module.entertainment.mode.guessstock.engine.MarketPriceEngine;
import com.order.menu.module.entertainment.mode.guessstock.engine.EventImpactSchedule;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;

/** Fast database-free distribution runner used by calibration and regression tests. */
public final class MarketSimulationRunner {
    private MarketSimulationRunner() {}

    public static void main(String[] args) {
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 10_000;
        int rounds = args.length > 1 ? Integer.parseInt(args[1]) : 100;
        Scenario scenario = args.length > 2 ? Scenario.valueOf(args[2]) : Scenario.FULL_MARKET;
        long seed = args.length > 3 ? Long.parseLong(args[3]) : 20260830L;
        System.out.println(runV2(games, rounds, 3, scenario, seed));
    }

    public static Report run(int games, int rounds, double baseVolatilityPct,
                             Scenario scenario, long seed) {
        return runInternal(games, rounds, baseVolatilityPct, scenario, seed, false);
    }

    public static Report runV2(int games, int rounds, double baseVolatilityPct,
                               Scenario scenario, long seed) {
        return runInternal(games, rounds, baseVolatilityPct, scenario, seed, true);
    }

    private static Report runInternal(int games, int rounds, double baseVolatilityPct,
                                      Scenario scenario, long seed, boolean v2) {
        if (games <= 0 || rounds <= 0) throw new IllegalArgumentException("games and rounds must be positive");
        List<GameResult> results = new ArrayList<>(games);
        for (int game = 0; game < games; game++) {
            SplittableRandom random = new SplittableRandom(mix(seed + game));
            results.add(v2 ? simulateV2(rounds, baseVolatilityPct, scenario, random)
                    : simulate(rounds, baseVolatilityPct, scenario, random));
        }
        double[] returns = results.stream().mapToDouble(GameResult::finalReturnPct).sorted().toArray();
        double[] drawdowns = results.stream().mapToDouble(GameResult::maxDrawdownPct).sorted().toArray();
        double[] stageMoves = results.stream().flatMapToDouble(item -> Arrays.stream(item.stageMovesPct()))
                .sorted().toArray();
        return new Report(games, rounds, scenario, v2 ? "V2" : "V1", average(returns), percentile(returns, .50),
                percentile(returns, .05), percentile(returns, .95),
                results.stream().filter(item -> item.finalReturnPct() > 0).count() * 100d / games,
                percentile(drawdowns, .50),
                results.stream().mapToDouble(GameResult::largestMovePct).average().orElse(0),
                percentile(stageMoves, .99),
                results.stream().mapToDouble(GameResult::realizedVolatilityPct).average().orElse(0),
                results.stream().mapToDouble(GameResult::regimeSwitches).average().orElse(0),
                results.stream().mapToDouble(GameResult::eventCount).average().orElse(0),
                results.stream().mapToDouble(GameResult::longestUpStreak).average().orElse(0),
                results.stream().mapToDouble(GameResult::longestDownStreak).average().orElse(0),
                averageStatePct(results, MarketStateCalculator.TrendState.STRONG_UP),
                averageStatePct(results, MarketStateCalculator.TrendState.UP),
                averageStatePct(results, MarketStateCalculator.TrendState.SIDEWAYS),
                averageStatePct(results, MarketStateCalculator.TrendState.DOWN),
                averageStatePct(results, MarketStateCalculator.TrendState.STRONG_DOWN),
                averageVolatilityPct(results, MarketStateCalculator.VolatilityState.LOW),
                averageVolatilityPct(results, MarketStateCalculator.VolatilityState.NORMAL),
                averageVolatilityPct(results, MarketStateCalculator.VolatilityState.HIGH),
                averageVolatilityPct(results, MarketStateCalculator.VolatilityState.EXTREME),
                results.stream().mapToDouble(GameResult::volatilitySwitches).average().orElse(0),
                results.stream().mapToDouble(GameResult::baseContributionPct).average().orElse(0),
                results.stream().mapToDouble(GameResult::eventContributionPct).average().orElse(0),
                results.stream().mapToDouble(GameResult::flowContributionPct).average().orElse(0));
    }

    private static GameResult simulateV2(int rounds, double baseVolatilityPct, Scenario scenario,
                                         SplittableRandom random) {
        List<BigDecimal> prices = new ArrayList<>();
        prices.add(BigDecimal.valueOf(100));
        MarketStateCalculator.MarketState state = MarketStateCalculator.calculate(prices, baseVolatilityPct, null);
        double[] eventSchedule = new double[rounds * 2 + 12];
        double price = 100;
        double high = price;
        double maxDrawdown = 0;
        double largestMove = 0;
        double baseSum = 0;
        double eventSum = 0;
        double flowSum = 0;
        double closeReturnSum = 0;
        double closeReturnSquareSum = 0;
        double[] stageMoves = new double[rounds * 2];
        int stageMoveIndex = 0;
        int switches = 0;
        int eventCount = 0;
        int upStreak = 0;
        int downStreak = 0;
        int longestUpStreak = 0;
        int longestDownStreak = 0;
        int[] trendStateCounts = new int[MarketStateCalculator.TrendState.values().length];
        int[] volatilityStateCounts = new int[MarketStateCalculator.VolatilityState.values().length];
        int volatilitySwitches = 0;
        MarketStateCalculator.TrendState priorTrend = state.trend();
        MarketStateCalculator.VolatilityState priorVolatility = state.volatility();
        int similarDirectionCount = 0;
        int previousEventDirection = 0;
        for (int round = 1; round <= rounds; round++) {
            if (allowsEvents(scenario) && random.nextDouble() < .20) {
                eventCount++;
                int direction = eventDirection(random, scenario, state);
                similarDirectionCount = direction == previousEventDirection ? similarDirectionCount + 1 : 0;
                previousEventDirection = direction;
                double saturation = 1d / (1d + similarDirectionCount * .35);
                String type = switch (random.nextInt(6)) {
                    case 0 -> "ACCIDENT"; case 1 -> "FINANCIAL"; case 2 -> "POLICY";
                    case 3 -> "DEMAND"; case 4 -> "SENTIMENT"; default -> "INDUSTRY";
                };
                BigDecimal total = BigDecimal.valueOf(direction * (1 + random.nextDouble() * 3));
                for (EventImpactSchedule.Slice slice : EventImpactSchedule.create(type, round, "OPENING",
                        total, saturation, 1)) {
                    int target = (slice.targetRound() - 1) * 2 + ("CLOSING".equals(slice.targetStage()) ? 1 : 0);
                    if (target < eventSchedule.length) eventSchedule[target] += slice.impactPct().doubleValue();
                }
            }
            double roundStartPrice = price;
            for (int stage = 0; stage < 2; stage++) {
                int scheduleIndex = (round - 1) * 2 + stage;
                double flow = flowImpact(random, scenario, baseVolatilityPct) * .45;
                MarketPriceEngine.Result result = MarketPriceEngine.price(new MarketPriceEngine.Context(
                        BigDecimal.valueOf(price), prices, state, baseVolatilityPct, 0,
                        eventSchedule[scheduleIndex], flow, Math.min(12, Math.max(3, baseVolatilityPct * 2.5)),
                        stage == 0, random.nextLong()));
                double move = result.totalImpactPct().doubleValue();
                stageMoves[stageMoveIndex++] = Math.abs(move);
                price = result.price().doubleValue();
                largestMove = Math.max(largestMove, Math.abs(move));
                baseSum += result.noiseImpactPct().doubleValue() + result.momentumImpactPct().doubleValue()
                        + result.meanReversionImpactPct().doubleValue();
                eventSum += result.eventImpactPct().doubleValue();
                flowSum += result.orderFlowImpactPct().doubleValue();
            }
            double closeReturn = (price / roundStartPrice - 1) * 100;
            closeReturnSum += closeReturn;
            closeReturnSquareSum += closeReturn * closeReturn;
            if (closeReturn > 0) {
                upStreak++;
                downStreak = 0;
            } else if (closeReturn < 0) {
                downStreak++;
                upStreak = 0;
            } else {
                upStreak = 0;
                downStreak = 0;
            }
            longestUpStreak = Math.max(longestUpStreak, upStreak);
            longestDownStreak = Math.max(longestDownStreak, downStreak);
            prices.add(BigDecimal.valueOf(price));
            if (prices.size() > 51) prices.remove(0);
            state = MarketStateCalculator.calculate(prices, baseVolatilityPct, state);
            if (priorTrend != state.trend()) switches++;
            if (priorVolatility != state.volatility()) volatilitySwitches++;
            priorTrend = state.trend();
            priorVolatility = state.volatility();
            trendStateCounts[state.trend().ordinal()]++;
            volatilityStateCounts[state.volatility().ordinal()]++;
            high = Math.max(high, price);
            maxDrawdown = Math.min(maxDrawdown, (price / high - 1) * 100);
        }
        return new GameResult((price / 100 - 1) * 100, maxDrawdown, largestMove,
                realizedVolatility(closeReturnSum, closeReturnSquareSum, rounds), switches, eventCount,
                longestUpStreak, longestDownStreak, trendStateCounts, volatilityStateCounts,
                volatilitySwitches, stageMoves, baseSum, eventSum, flowSum);
    }

    private static boolean allowsEvents(Scenario scenario) {
        return scenario != Scenario.NO_EVENT_NO_FLOW && scenario != Scenario.NO_EVENT_BALANCED_FLOW;
    }

    private static int eventDirection(SplittableRandom random, Scenario scenario,
                                      MarketStateCalculator.MarketState state) {
        if (scenario == Scenario.ONLY_POSITIVE_EVENTS) return 1;
        if (scenario == Scenario.ONLY_NEGATIVE_EVENTS) return -1;
        if (scenario == Scenario.BALANCED_EVENTS_NO_FLOW) return random.nextBoolean() ? 1 : -1;
        boolean up = state.trend() == MarketStateCalculator.TrendState.UP
                || state.trend() == MarketStateCalculator.TrendState.STRONG_UP;
        if (state.trend() == MarketStateCalculator.TrendState.SIDEWAYS) return random.nextBoolean() ? 1 : -1;
        return random.nextDouble() < .55 ? (up ? 1 : -1) : (up ? -1 : 1);
    }

    private static GameResult simulate(int rounds, double baseVolatilityPct, Scenario scenario,
                                       SplittableRandom random) {
        List<BigDecimal> prices = new ArrayList<>();
        prices.add(BigDecimal.valueOf(100));
        MarketStateCalculator.MarketState state = null;
        MarketStateCalculator.TrendState priorTrend = MarketStateCalculator.TrendState.SIDEWAYS;
        double price = 100;
        double high = price;
        double maxDrawdown = 0;
        double largestMove = 0;
        double baseSum = 0;
        double eventSum = 0;
        double flowSum = 0;
        double returnSum = 0;
        double returnSquareSum = 0;
        double[] stageMoves = new double[rounds];
        int switches = 0;
        int eventCount = 0;
        int upStreak = 0;
        int downStreak = 0;
        int longestUpStreak = 0;
        int longestDownStreak = 0;
        int[] trendStateCounts = new int[MarketStateCalculator.TrendState.values().length];
        int[] volatilityStateCounts = new int[MarketStateCalculator.VolatilityState.values().length];
        int volatilitySwitches = 0;
        MarketStateCalculator.VolatilityState priorVolatility = null;
        for (int round = 1; round <= rounds; round++) {
            double base = truncatedGaussian(random, baseVolatilityPct);
            double event = eventImpact(random, scenario, state, round);
            if (event != 0) eventCount++;
            double flow = flowImpact(random, scenario, baseVolatilityPct);
            double total = clamp(base + event + flow, -12, 12);
            stageMoves[round - 1] = Math.abs(total);
            returnSum += total;
            returnSquareSum += total * total;
            if (total > 0) {
                upStreak++;
                downStreak = 0;
            } else if (total < 0) {
                downStreak++;
                upStreak = 0;
            } else {
                upStreak = 0;
                downStreak = 0;
            }
            longestUpStreak = Math.max(longestUpStreak, upStreak);
            longestDownStreak = Math.max(longestDownStreak, downStreak);
            price *= Math.exp(total / 100d);
            prices.add(BigDecimal.valueOf(price));
            state = MarketStateCalculator.calculate(prices, baseVolatilityPct, state);
            if (round > 1 && priorTrend != state.trend()) switches++;
            if (priorVolatility != null && priorVolatility != state.volatility()) volatilitySwitches++;
            priorTrend = state.trend();
            priorVolatility = state.volatility();
            trendStateCounts[state.trend().ordinal()]++;
            volatilityStateCounts[state.volatility().ordinal()]++;
            high = Math.max(high, price);
            maxDrawdown = Math.min(maxDrawdown, (price / high - 1) * 100);
            largestMove = Math.max(largestMove, Math.abs(total));
            baseSum += base;
            eventSum += event;
            flowSum += flow;
        }
        return new GameResult((price / 100 - 1) * 100, maxDrawdown, largestMove,
                realizedVolatility(returnSum, returnSquareSum, rounds), switches, eventCount,
                longestUpStreak, longestDownStreak, trendStateCounts, volatilityStateCounts,
                volatilitySwitches, stageMoves, baseSum, eventSum, flowSum);
    }

    private static double eventImpact(SplittableRandom random, Scenario scenario,
                                      MarketStateCalculator.MarketState state, int round) {
        if (scenario == Scenario.NO_EVENT_NO_FLOW || scenario == Scenario.NO_EVENT_BALANCED_FLOW
                || random.nextDouble() >= .20) return 0;
        int direction = switch (scenario) {
            case ONLY_POSITIVE_EVENTS -> 1;
            case ONLY_NEGATIVE_EVENTS -> -1;
            default -> {
                if (state == null || state.trend() == MarketStateCalculator.TrendState.SIDEWAYS) {
                    yield random.nextBoolean() ? 1 : -1;
                }
                boolean upTrend = state.trend() == MarketStateCalculator.TrendState.UP
                        || state.trend() == MarketStateCalculator.TrendState.STRONG_UP;
                yield random.nextDouble() < .65 ? (upTrend ? 1 : -1) : (upTrend ? -1 : 1);
            }
        };
        double magnitude = 1 + random.nextDouble() * 3;
        if (state != null && state.trend() != MarketStateCalculator.TrendState.SIDEWAYS) {
            boolean same = direction > 0 == (state.trend() == MarketStateCalculator.TrendState.UP
                    || state.trend() == MarketStateCalculator.TrendState.STRONG_UP);
            magnitude *= same ? 1.10 : .70;
        }
        return direction * magnitude;
    }

    private static double flowImpact(SplittableRandom random, Scenario scenario, double baseVolatilityPct) {
        if (scenario == Scenario.NO_EVENT_NO_FLOW || scenario == Scenario.BALANCED_EVENTS_NO_FLOW
                || scenario == Scenario.ONLY_POSITIVE_EVENTS || scenario == Scenario.ONLY_NEGATIVE_EVENTS) return 0;
        return truncatedGaussian(random, Math.max(.2, baseVolatilityPct * .35));
    }

    private static double truncatedGaussian(SplittableRandom random, double maximumAbsolute) {
        double u1 = Math.max(1.0e-12, random.nextDouble());
        double gaussian = Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * random.nextDouble());
        return clamp(gaussian * maximumAbsolute / 2, -maximumAbsolute, maximumAbsolute);
    }

    private static double average(double[] values) { return Arrays.stream(values).average().orElse(0); }
    private static double realizedVolatility(double sum, double squareSum, int count) {
        if (count <= 0) return 0;
        double mean = sum / count;
        return Math.sqrt(Math.max(0, squareSum / count - mean * mean));
    }
    private static double averageStatePct(List<GameResult> values, MarketStateCalculator.TrendState state) {
        return values.stream().mapToDouble(item -> item.trendStateCounts()[state.ordinal()] * 100d
                / Math.max(1, Arrays.stream(item.trendStateCounts()).sum())).average().orElse(0);
    }
    private static double averageVolatilityPct(List<GameResult> values, MarketStateCalculator.VolatilityState state) {
        return values.stream().mapToDouble(item -> item.volatilityStateCounts()[state.ordinal()] * 100d
                / Math.max(1, Arrays.stream(item.volatilityStateCounts()).sum())).average().orElse(0);
    }
    private static double percentile(double[] values, double percentile) {
        if (values.length == 0) return 0;
        int index = (int) Math.round((values.length - 1) * percentile);
        return values[Math.max(0, Math.min(values.length - 1, index))];
    }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    public enum Scenario {
        NO_EVENT_NO_FLOW,
        NO_EVENT_BALANCED_FLOW,
        BALANCED_EVENTS_NO_FLOW,
        ONLY_POSITIVE_EVENTS,
        ONLY_NEGATIVE_EVENTS,
        FULL_MARKET
    }

    private record GameResult(double finalReturnPct, double maxDrawdownPct, double largestMovePct,
                              double realizedVolatilityPct, int regimeSwitches, int eventCount,
                              int longestUpStreak, int longestDownStreak, int[] trendStateCounts,
                              int[] volatilityStateCounts, int volatilitySwitches,
                              double[] stageMovesPct, double baseContributionPct,
                              double eventContributionPct, double flowContributionPct) {}

    public record Report(int games, int rounds, Scenario scenario, String engineVersion, double averageReturnPct,
                         double medianReturnPct, double p05ReturnPct, double p95ReturnPct,
                         double positiveGamePct, double medianMaxDrawdownPct,
                         double averageLargestMovePct, double p99AbsoluteStageMovePct,
                         double averageRealizedVolatilityPct, double averageRegimeSwitches,
                         double averageEventCount, double averageLongestUpStreak,
                         double averageLongestDownStreak, double strongUpRoundPct,
                         double upRoundPct, double sidewaysRoundPct, double downRoundPct,
                         double strongDownRoundPct, double lowVolatilityRoundPct,
                         double normalVolatilityRoundPct, double highVolatilityRoundPct,
                         double extremeVolatilityRoundPct, double averageVolatilitySwitches,
                         double averageBaseContributionPct, double averageEventContributionPct,
                         double averageFlowContributionPct) {}
}
