package com.order.menu.module.entertainment.mode.guessstock.simulation;

import com.order.menu.module.entertainment.mode.guessstock.engine.AiTradingPolicy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/** Database and model-provider free AI behavior calibration harness. */
public final class AiBattleSimulationRunner {
    private AiBattleSimulationRunner() {}

    public static Report run(int games, int rounds, String difficulty, long seed) {
        List<GameResult> results = new ArrayList<>(games);
        for (int game = 0; game < games; game++) {
            results.add(simulate(rounds, difficulty, new SplittableRandom(mix(seed + game))));
        }
        double[] returns = results.stream().mapToDouble(GameResult::returnPct).sorted().toArray();
        double averageReturn = java.util.Arrays.stream(returns).average().orElse(0);
        double returnDeviation = standardDeviation(returns, averageReturn);
        return new Report(difficulty, games, rounds, averageReturn, median(returns),
                returnDeviation == 0 ? 0 : averageReturn / returnDeviation,
                results.stream().mapToDouble(GameResult::maxDrawdownPct).average().orElse(0),
                results.stream().filter(item -> item.returnPct() > 0).count() * 100d / games,
                results.stream().mapToDouble(GameResult::tradeCount).average().orElse(0),
                results.stream().mapToDouble(GameResult::turnoverPct).average().orElse(0),
                results.stream().mapToDouble(GameResult::holdingRoundRatio).average().orElse(0),
                results.stream().mapToDouble(GameResult::averageHoldingRounds).average().orElse(0),
                results.stream().mapToDouble(GameResult::reverseTrades).average().orElse(0),
                results.stream().mapToDouble(GameResult::decisionChecks).average().orElse(0));
    }

    private static GameResult simulate(int rounds, String difficulty, SplittableRandom random) {
        double cash = 10_000;
        long shares = 0;
        double averageCost = 0;
        double price = 100;
        double peakAsset = cash;
        double maxDrawdown = 0;
        double turnover = 0;
        int trades = 0;
        int holdingRounds = 0;
        int holdingSamples = 0;
        int holdingEpisodes = 0;
        int reverseTrades = 0;
        String lastDirection = null;
        int lastTradeRound = -100;
        int decisionChecks = 0;
        int reviewInterval = switch (difficulty) { case "EASY" -> 10; case "HARD" -> 5; default -> 7; };
        List<AiTradingPolicy.MarketPoint> history = new ArrayList<>();
        for (int round = 1; round <= rounds; round++) {
            double eventSignal = random.nextDouble() < .18 ? (random.nextBoolean() ? 1 : -1) : 0;
            String eventName = eventSignal > 0 ? "订单增长" : eventSignal < 0 ? "需求下滑" : "无事件";
            boolean review = round == 1 || round % reviewInterval == 0
                    || (eventSignal != 0 && random.nextDouble() < .15);
            AiTradingPolicy.Context opening = context(difficulty, round, rounds, "OPENING", price,
                    averageCost, cash, shares, history, eventName, random.nextLong());
            AiTradingPolicy.Decision openingDecision = AiTradingPolicy.decide(opening);
            if (review) decisionChecks++;
            if (review && "BUY".equals(openingDecision.action()) && cash >= price
                    && (lastDirection == null || "BUY".equals(lastDirection) || round - lastTradeRound >= 2)) {
                long bought = Math.min((long) (cash / price), Math.max(1,
                        (long) Math.floor(cash / price * openingDecision.riskFraction() * .35)));
                if (bought > 0) {
                    if (shares == 0) holdingEpisodes++;
                    double amount = bought * price;
                    averageCost = (averageCost * shares + amount) / (shares + bought);
                    shares += bought;
                    cash -= amount;
                    turnover += amount;
                    trades++;
                    if (lastDirection != null && !"BUY".equals(lastDirection)) reverseTrades++;
                    lastDirection = "BUY";
                    lastTradeRound = round;
                }
            }
            double returnPct = gaussian(random) * 1.2 + eventSignal * (.3 + random.nextDouble() * 1.2);
            double close = Math.max(.01, price * Math.exp(returnPct / 100d));
            AiTradingPolicy.Context closing = context(difficulty, round, rounds, "CLOSING", close,
                    averageCost, cash, shares, history, eventName, random.nextLong());
            AiTradingPolicy.Decision closingDecision = AiTradingPolicy.decide(closing);
            if (review) decisionChecks++;
            if (review && lastTradeRound != round && "SELL".equals(closingDecision.action()) && shares > 0
                    && (lastDirection == null || "SELL".equals(lastDirection) || round - lastTradeRound >= 2)) {
                long sold = Math.min(shares, Math.max(1,
                        (long) Math.floor(shares * closingDecision.riskFraction() * .35)));
                cash += sold * close;
                shares -= sold;
                turnover += sold * close;
                trades++;
                if (lastDirection != null && !"SELL".equals(lastDirection)) reverseTrades++;
                lastDirection = "SELL";
                lastTradeRound = round;
                if (shares == 0) averageCost = 0;
            }
            if (shares > 0) holdingRounds++;
            holdingSamples++;
            history.add(new AiTradingPolicy.MarketPoint(BigDecimal.valueOf(price), BigDecimal.valueOf(close)));
            if (history.size() > 20) history.remove(0);
            price = close;
            double asset = cash + shares * price;
            peakAsset = Math.max(peakAsset, asset);
            maxDrawdown = Math.min(maxDrawdown, (asset / peakAsset - 1) * 100);
        }
        double finalAsset = cash + shares * price;
        return new GameResult((finalAsset / 10_000 - 1) * 100, maxDrawdown, trades,
                turnover / 10_000 * 100, holdingSamples == 0 ? 0 : holdingRounds / (double) holdingSamples,
                holdingEpisodes == 0 ? 0 : holdingRounds / (double) holdingEpisodes,
                reverseTrades, decisionChecks);
    }

    private static AiTradingPolicy.Context context(String difficulty, int round, int rounds, String stage,
                                                    double price, double averageCost, double cash, long shares,
                                                    List<AiTradingPolicy.MarketPoint> history,
                                                    String eventName, long seed) {
        return new AiTradingPolicy.Context("RANDOM", difficulty, round, rounds, stage,
                BigDecimal.valueOf(price), BigDecimal.valueOf(averageCost), BigDecimal.valueOf(cash), shares,
                List.copyOf(history), 0, 0, 1, 4, eventName, eventName,
                3, 1000, new AiTradingPolicy.Memory(0, 0, 0), seed, false);
    }

    private static double gaussian(SplittableRandom random) {
        double u1 = Math.max(1.0e-12, random.nextDouble());
        return Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * random.nextDouble());
    }
    private static double median(double[] values) { return values.length == 0 ? 0 : values[values.length / 2]; }
    private static double standardDeviation(double[] values, double mean) {
        return Math.sqrt(java.util.Arrays.stream(values).map(value -> Math.pow(value - mean, 2))
                .average().orElse(0));
    }
    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private record GameResult(double returnPct, double maxDrawdownPct, int tradeCount,
                              double turnoverPct, double holdingRoundRatio, double averageHoldingRounds,
                              int reverseTrades, int decisionChecks) {}

    public record Report(String difficulty, int games, int rounds, double averageReturnPct,
                         double medianReturnPct, double sharpeLike, double averageMaxDrawdownPct, double winRatePct,
                         double averageTradeCount, double averageTurnoverPct,
                         double averageHoldingRoundRatio, double averageHoldingRounds,
                         double averageReverseTrades,
                         double averageDecisionChecks) {}
}
