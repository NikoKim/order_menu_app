package com.order.menu.module.entertainment.mode.guessstock.simulation;

import com.order.menu.module.entertainment.mode.guessstock.agent.AiDifficultyPolicy;
import com.order.menu.module.entertainment.mode.guessstock.agent.AiTargetPositionExecutor;
import com.order.menu.module.entertainment.mode.guessstock.engine.MarketPriceEngine;
import com.order.menu.module.entertainment.mode.guessstock.engine.MarketStateCalculator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/** Paired-seed battle harness that reuses the production V2 market state, price and execution engines. */
public final class GuessStockBattleSimulator {
    private static final BigDecimal INITIAL_CASH = new BigDecimal("10000");
    private static final AiTargetPositionExecutor EXECUTOR = new AiTargetPositionExecutor();
    private GuessStockBattleSimulator() {}

    public static Report run(int games, int rounds, String difficulty, long seed) {
        if (games <= 0 || rounds <= 0) throw new IllegalArgumentException("games and rounds must be positive");
        Map<Strategy, Aggregate> totals = new EnumMap<>(Strategy.class);
        for (Strategy strategy : Strategy.values()) totals.put(strategy, new Aggregate());
        for (int game = 0; game < games; game++) {
            List<MarketRound> market = market(rounds, mix(seed + game));
            for (Strategy strategy : Strategy.values()) totals.get(strategy).add(play(market, strategy, difficulty));
        }
        Map<Strategy, StrategyReport> reports = new EnumMap<>(Strategy.class);
        totals.forEach((strategy, total) -> reports.put(strategy, total.report(games)));
        return new Report(games, rounds, difficulty, Map.copyOf(reports));
    }

    /** Runs all difficulty levels on the exact same market paths and reports paired score wins. */
    public static DifficultyLeagueReport runDifficultyLeague(int games, int rounds, long seed) {
        if (games <= 0 || rounds <= 0) throw new IllegalArgumentException("games and rounds must be positive");
        Map<String, Aggregate> totals = new java.util.LinkedHashMap<>();
        totals.put("EASY", new Aggregate());
        totals.put("NORMAL", new Aggregate());
        totals.put("HARD", new Aggregate());
        double normalOverEasy = 0;
        double hardOverNormal = 0;
        for (int game = 0; game < games; game++) {
            List<MarketRound> market = market(rounds, mix(seed + game));
            PortfolioResult easy = play(market, Strategy.THESIS_AGENT, "EASY");
            PortfolioResult normal = play(market, Strategy.THESIS_AGENT, "NORMAL");
            PortfolioResult hard = play(market, Strategy.THESIS_AGENT, "HARD");
            totals.get("EASY").add(easy);
            totals.get("NORMAL").add(normal);
            totals.get("HARD").add(hard);
            normalOverEasy += pairedPoint(riskAdjustedScore(normal), riskAdjustedScore(easy));
            hardOverNormal += pairedPoint(riskAdjustedScore(hard), riskAdjustedScore(normal));
        }
        Map<String, StrategyReport> reports = new java.util.LinkedHashMap<>();
        totals.forEach((difficulty, total) -> reports.put(difficulty, total.report(games)));
        return new DifficultyLeagueReport(games, rounds, Map.copyOf(reports),
                normalOverEasy * 100d / games, hardOverNormal * 100d / games);
    }

    private static double riskAdjustedScore(PortfolioResult result) {
        return result.returnPct() + result.maxDrawdownPct() * .20 - result.trades() * .01;
    }

    private static double pairedPoint(double left, double right) {
        if (Math.abs(left - right) < 1e-9) return .5;
        return left > right ? 1 : 0;
    }

    private static List<MarketRound> market(int rounds, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        List<BigDecimal> prices = new ArrayList<>();
        prices.add(new BigDecimal("100"));
        MarketStateCalculator.MarketState state = MarketStateCalculator.calculate(prices, 3, null);
        List<MarketRound> result = new ArrayList<>(rounds);
        BigDecimal price = prices.get(0);
        for (int round = 1; round <= rounds; round++) {
            int eventDirection = random.nextDouble() < .18 ? (random.nextBoolean() ? 1 : -1) : 0;
            double event = eventDirection == 0 ? 0 : eventDirection * (.6 + random.nextDouble() * 2.2);
            var opening = MarketPriceEngine.price(new MarketPriceEngine.Context(price, prices, state, 3,
                    0, event * .4, 0, 8, true, random.nextLong()));
            var closing = MarketPriceEngine.price(new MarketPriceEngine.Context(opening.price(), prices, state, 3,
                    0, event * .6, 0, 8, false, random.nextLong()));
            price = closing.price();
            prices.add(price);
            if (prices.size() > 51) prices.remove(0);
            state = MarketStateCalculator.calculate(prices, 3, state);
            result.add(new MarketRound(round, opening.price(), closing.price(), eventDirection, state));
        }
        return List.copyOf(result);
    }

    private static PortfolioResult play(List<MarketRound> market, Strategy strategy, String difficulty) {
        AiDifficultyPolicy policy = AiDifficultyPolicy.forName(difficulty);
        BigDecimal cash = INITIAL_CASH;
        long shares = 0;
        double peak = INITIAL_CASH.doubleValue();
        double drawdown = 0;
        int trades = 0;
        Integer lastTradeRound = null;
        String lastDirection = null;
        List<BigDecimal> closes = new ArrayList<>();
        for (MarketRound round : market) {
            BigDecimal openAssets = cash.add(round.open().multiply(BigDecimal.valueOf(shares)));
            double currentPct = exposure(cash, shares, round.open());
            double target = target(strategy, policy, round, closes, currentPct, market.size());
            String stage = target >= currentPct ? "OPENING" : "CLOSING";
            BigDecimal executionPrice = "OPENING".equals(stage) ? round.open() : round.close();
            BigDecimal assets = cash.add(executionPrice.multiply(BigDecimal.valueOf(shares)));
            var plan = EXECUTOR.plan(guard(policy), new AiTargetPositionExecutor.Context(stage, round.roundNo(),
                    target, exposure(cash, shares, executionPrice), shares, cash, assets, openAssets,
                    BigDecimal.ZERO, executionPrice, "MEDIUM", null, "NONE", false, false,
                    new BigDecimal("100000"), 7, new BigDecimal("3"), lastTradeRound, lastDirection, false));
            if (plan.shares() > 0) {
                BigDecimal amount = executionPrice.multiply(BigDecimal.valueOf(plan.shares()));
                if ("BUY".equals(plan.direction())) { cash = cash.subtract(amount); shares += plan.shares(); }
                else { cash = cash.add(amount); shares -= plan.shares(); }
                trades++;
                lastTradeRound = round.roundNo();
                lastDirection = plan.direction();
            }
            closes.add(round.close());
            double asset = cash.doubleValue() + shares * round.close().doubleValue();
            peak = Math.max(peak, asset);
            drawdown = Math.min(drawdown, (asset / peak - 1) * 100);
        }
        BigDecimal finalPrice = market.get(market.size() - 1).close();
        double finalAsset = cash.doubleValue() + shares * finalPrice.doubleValue();
        return new PortfolioResult((finalAsset / INITIAL_CASH.doubleValue() - 1) * 100, drawdown, trades);
    }

    private static double target(Strategy strategy, AiDifficultyPolicy policy, MarketRound round,
                                 List<BigDecimal> closes, double current, int totalRounds) {
        double recent = closes.size() < 3 ? 0 : MarketStateCalculator.periodReturn(closes, 3);
        return switch (strategy) {
            case CASH_ONLY -> 0;
            case BUY_AND_HOLD -> 100;
            case DCA -> Math.min(100, round.roundNo() * 100d / Math.max(1, totalRounds / 2));
            case EVENT_FOLLOW -> round.eventDirection() > 0 ? 90 : round.eventDirection() < 0 ? 10 : current;
            case MEAN_REVERSION -> recent < -1.2 ? 80 : recent > 1.2 ? 20 : current;
            case TREND_FOLLOW -> trendTarget(round.state());
            case THESIS_AGENT -> {
                if (round.roundNo() % policy.reviewIntervalRounds() != 0 && round.eventDirection() == 0) yield current;
                double trend = round.state().trendScore();
                double eventQuality = policy.publicHistoryRounds() >= 50 ? 1.20
                        : policy.publicHistoryRounds() >= 20 ? .65 : .45;
                double signal = trend + round.eventDirection() * eventQuality;
                if (signal > policy.signalThreshold()) yield policy.maxPositionPct();
                if (signal < -policy.signalThreshold()) yield 5;
                yield Math.min(policy.maxPositionPct(), 45);
            }
        };
    }

    private static double trendTarget(MarketStateCalculator.MarketState state) {
        return switch (state.trend()) {
            case STRONG_UP -> 100; case UP -> 75; case SIDEWAYS -> 45; case DOWN -> 20; case STRONG_DOWN -> 0;
        };
    }

    private static AiTargetPositionExecutor.Guard guard(AiDifficultyPolicy policy) {
        return new AiTargetPositionExecutor.Guard(BigDecimal.valueOf(policy.maxPositionPct()),
                new BigDecimal("3"), 1, new BigDecimal("1"), new BigDecimal("80"), 10000,
                new BigDecimal("3"), new BigDecimal("25"), new BigDecimal("45"), 1);
    }

    private static double exposure(BigDecimal cash, long shares, BigDecimal price) {
        double stock = shares * price.doubleValue();
        return stock <= 0 ? 0 : stock / Math.max(.01, stock + cash.doubleValue()) * 100;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    public enum Strategy { CASH_ONLY, BUY_AND_HOLD, DCA, EVENT_FOLLOW, MEAN_REVERSION, TREND_FOLLOW, THESIS_AGENT }
    private record MarketRound(int roundNo, BigDecimal open, BigDecimal close, int eventDirection,
                               MarketStateCalculator.MarketState state) {}
    private record PortfolioResult(double returnPct, double maxDrawdownPct, int trades) {}
    private static final class Aggregate {
        double returns; double squares; double drawdowns; double trades; int wins;
        void add(PortfolioResult value) { returns += value.returnPct(); squares += value.returnPct() * value.returnPct();
            drawdowns += value.maxDrawdownPct(); trades += value.trades(); if (value.returnPct() > 0) wins++; }
        StrategyReport report(int games) { double mean = returns / games;
            double deviation = Math.sqrt(Math.max(0, squares / games - mean * mean));
            return new StrategyReport(mean, deviation == 0 ? 0 : mean / deviation, drawdowns / games,
                    trades / games, wins * 100d / games); }
    }
    public record StrategyReport(double averageReturnPct, double sharpeLike, double averageMaxDrawdownPct,
                                 double averageTrades, double winRatePct) {}
    public record Report(int games, int rounds, String difficulty, Map<Strategy, StrategyReport> strategies) {}
    public record DifficultyLeagueReport(int games, int rounds, Map<String, StrategyReport> difficulties,
                                         double normalOverEasyWinRatePct, double hardOverNormalWinRatePct) {}
}
