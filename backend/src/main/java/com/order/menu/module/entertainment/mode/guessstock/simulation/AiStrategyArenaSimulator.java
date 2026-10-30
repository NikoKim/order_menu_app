package com.order.menu.module.entertainment.mode.guessstock.simulation;

import com.order.menu.module.entertainment.mode.guessstock.agent.*;
import com.order.menu.module.entertainment.mode.guessstock.engine.*;
import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockRound;
import com.order.menu.module.entertainment.mode.guessstock.fundamental.FundamentalEngine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Eight public-information tactical agents against one baseline. No model calls or hidden-state observations. */
public final class AiStrategyArenaSimulator {
    private static final BigDecimal INITIAL = new BigDecimal("10000");
    private static final BigDecimal FEE = new BigDecimal("0.05");
    private static final BigDecimal SLIP = new BigDecimal("0.10");
    private static final BigDecimal IMPACT = new BigDecimal("2");
    private static final long LIQUIDITY = 4500;
    private static final String[] STYLES = {"CONSERVATIVE", "TREND", "CONTRARIAN", "AGGRESSIVE",
            "RANDOM", "TREND", "CONTRARIAN", "AGGRESSIVE"};
    private AiStrategyArenaSimulator() {}

    public static Report run(int games, int rounds, String difficulty, Baseline baseline, long seed) {
        return run(games, rounds, difficulty, baseline, seed, 100);
    }

    public static Report runWithNeutralModelObservations(int games, int rounds, String difficulty, Baseline baseline, long seed) {
        return run(games, rounds, difficulty, baseline, seed, 0);
    }

    private static Report run(int games, int rounds, String difficulty, Baseline baseline, long seed, double modelTarget) {
        if (games <= 0 || rounds < 2) throw new IllegalArgumentException("invalid simulation size");
        int aiFirst = 0, humanLast = 0, trades = 0, reviews = 0, probes = 0, accumulations = 0, exits = 0;
        double aiReturns = 0, humanReturns = 0, maxDrawdowns = 0, bestAiReturns = 0, individualWins = 0;
        for (int game = 0; game < games; game++) {
            GameResult result = play(rounds, difficulty, baseline, seed + game * 104729L, modelTarget);
            aiFirst += result.aiFirst() ? 1 : 0;
            humanLast += result.humanLast() ? 1 : 0;
            trades += result.trades(); reviews += result.reviews();
            probes += result.probes(); accumulations += result.accumulations(); exits += result.exits();
            aiReturns += result.averageAiReturn(); humanReturns += result.humanReturn();
            bestAiReturns += result.bestAiReturn(); maxDrawdowns += result.averageAiDrawdown();
            individualWins += result.aiBeatHumanPct();
        }
        return new Report(games, rounds, difficulty, baseline, aiFirst * 100d / games,
                humanLast * 100d / games, aiReturns / games, bestAiReturns / games, humanReturns / games,
                maxDrawdowns / games, trades / (double) games, reviews / (double) games,
                probes, accumulations, exits, individualWins / games);
    }

    public static LeagueReport difficultyLeague(int games, int rounds, Baseline baseline, long seed) {
        double normalWins = 0, hardWins = 0, easyReturns = 0, normalReturns = 0, hardReturns = 0;
        for (int game = 0; game < games; game++) {
            long gameSeed = seed + game * 104729L;
            var easy = play(rounds, "EASY", baseline, gameSeed);
            var normal = play(rounds, "NORMAL", baseline, gameSeed);
            var hard = play(rounds, "HARD", baseline, gameSeed);
            normalWins += point(score(normal), score(easy));
            hardWins += point(score(hard), score(normal));
            easyReturns += easy.averageAiReturn(); normalReturns += normal.averageAiReturn(); hardReturns += hard.averageAiReturn();
        }
        return new LeagueReport(games, rounds, normalWins * 100 / games, hardWins * 100 / games,
                easyReturns / games, normalReturns / games, hardReturns / games);
    }

    private static double score(GameResult result) { return result.averageAiReturn() + result.averageAiDrawdown() * .20; }
    private static double point(double a, double b) { return Math.abs(a - b) < 1e-9 ? .5 : a > b ? 1 : 0; }

    private static GameResult play(int rounds, String difficulty, Baseline baseline, long seed) {
        return play(rounds, difficulty, baseline, seed, 100);
    }

    private static GameResult play(int rounds, String difficulty, Baseline baseline, long seed, double modelTarget) {
        SplittableRandom random = new SplittableRandom(seed);
        List<Actor> actors = new ArrayList<>();
        for (int i = 0; i < 8; i++) actors.add(new Actor("AI" + i, STYLES[i]));
        actors.add(new Actor("HUMAN", "BASELINE"));
        List<GuessStockRound> history = new ArrayList<>();
        List<BigDecimal> closes = new ArrayList<>(List.of(new BigDecimal("100")));
        var marketState = MarketStateCalculator.calculate(closes, 3, null);
        FundamentalEngine.State internalFundamentals = FundamentalEngine.neutral();
        AiMarketSnapshotService snapshots = new AiMarketSnapshotService();
        AiTargetPositionExecutor executor = new AiTargetPositionExecutor();
        AiModelSettings modelSettings = new AiModelSettings();
        BigDecimal price = new BigDecimal("100");
        double publishedFair = 0;
        int reportRound = 0, reviews = 0, probes = 0, accumulations = 0, exits = 0;
        long publicBuys = 0, publicSells = 0;
        double buyConcentration = 0;
        double drift = 0;
        for (int roundNo = 1; roundNo <= rounds; roundNo++) {
            if ((roundNo - 1) % 18 == 0) drift = random.nextDouble(-.22, .22);
            GuessStockRound round = new GuessStockRound();
            round.setRoundNo(roundNo); round.setPreviousClosePrice(price);
            long roundBuys = 0, roundSells = 0, largestBuy = 0;
            for (Actor actor : actors) { actor.roundStartAssets = actor.assets(price); actor.turnover = BigDecimal.ZERO; }
            for (int stageIndex = 0; stageIndex < 2; stageIndex++) {
                String stage = stageIndex == 0 ? "OPENING" : "CLOSING";
                boolean event = random.nextDouble() < .12;
                boolean up = random.nextBoolean();
                String type = random.nextBoolean() ? "DEMAND" : "FINANCIAL";
                round.setEventStage(stage);
                round.setEventName(event ? (up ? "订单增长" : "需求下滑") : "无事件");
                round.setEventDescription(event ? "公开经营消息，尚待后续验证" : "无");
                round.setEventDirection(up ? "UP" : "DOWN");
                round.setEventType(type); round.setEventStrength("MEDIUM");
                round.setEventCredibility("PRELIMINARY"); round.setEventSurprise("NORMAL"); round.setEventRelevance("MEDIUM");
                if (stageIndex == 0) round.setOpeningEventOccurred(event ? 1 : 0);
                else round.setClosingEventOccurred(event ? 1 : 0);
                // Only completed rounds, the current reference price, and the current public announcement are observed.
                var snapshot = snapshots.create(roundNo, rounds, stage, price, round.getPreviousClosePrice(),
                        history, round, publicBuys, publicSells, 3);
                List<AuctionClearingEngine.TargetOrder> orders = new ArrayList<>();
                for (int i = 0; i < actors.size(); i++) {
                    Actor actor = actors.get(i);
                    if (i == 8) {
                        orders.add(new AuctionClearingEngine.TargetOrder(actor.key, actor.cash, actor.shares,
                                baselineTarget(baseline, snapshot, actor, rounds), BigDecimal.ZERO));
                        continue;
                    }
                    var context = new AiStrategyPlanner.Context(snapshot, actor.style, difficulty, actor.exposure(price),
                            100, actor.shares, actor.averageCost, actor.assets(price).doubleValue(), publishedFair,
                            reportRound, 0, 0, "SAFE", 0, FEE.doubleValue(), SLIP.doubleValue(), 0,
                            LIQUIDITY, IMPACT.doubleValue(), buyConcentration);
                    var plan = AiStrategyPlanner.evaluate(context, actor.strategy);
                    if (AiModelCallPolicy.shouldCall(actor.strategy, snapshot, false,
                            actor.strategy != null && reportRound > actor.strategy.reportRound(), false, modelSettings)) {
                        reviews++;
                        // Count review opportunities only. This harness does not pretend to execute an LLM.
                        plan = AiStrategyPlanner.evaluate(context, plan.state().withModel(roundNo, modelTarget, "synthetic review", false));
                    }
                    actor.strategy = plan.state();
                    if (plan.transition()) {
                        if ("PROBE".equals(plan.state().phase())) probes++;
                        if ("ACCUMULATE".equals(plan.state().phase())) accumulations++;
                        if ("EXIT".equals(plan.state().phase())) exits++;
                    }
                    boolean conservative = "CONSERVATIVE".equals(actor.style);
                    boolean aggressive = "AGGRESSIVE".equals(actor.style);
                    var guard = new AiTargetPositionExecutor.Guard(new BigDecimal("100"),
                            BigDecimal.valueOf(conservative ? 7 : aggressive ? 3 : 5), conservative ? 2 : aggressive ? 0 : 1,
                            new BigDecimal("2"), BigDecimal.valueOf(conservative ? 20 : aggressive ? 40 : 30),
                            1000, new BigDecimal("2"), new BigDecimal("15"), new BigDecimal("30"), 2);
                    String roundDirection = actor.lastTradeRound != null && actor.lastTradeRound == roundNo ? actor.lastSide : "NONE";
                    var execution = executor.plan(guard, new AiTargetPositionExecutor.Context(stage, roundNo, plan.target(),
                            actor.exposure(price), actor.shares, actor.cash, actor.assets(price), actor.roundStartAssets,
                            actor.turnover, price, plan.urgency(), actor.lastBuyRound, roundDirection, false, plan.risk(),
                            BigDecimal.valueOf(LIQUIDITY / 9), 9, IMPACT, actor.lastTradeRound, actor.lastSide,
                            snapshot.regimeChanged(), true));
                    if (execution.shares() > 0) {
                        BigDecimal capacity = actor.roundStartAssets.multiply(guard.maxRoundTurnoverPct())
                                .divide(new BigDecimal("100"), 8, RoundingMode.DOWN).subtract(actor.turnover).max(BigDecimal.ZERO);
                        orders.add(new AuctionClearingEngine.TargetOrder(actor.key, actor.cash, actor.shares,
                                execution.targetPositionPct(), BigDecimal.ZERO, execution.direction(), execution.shares(),
                                capacity, null, "SELL".equals(execution.direction()) ? BigDecimal.valueOf(plan.minSellPrice()) : null,
                                "BUY".equals(execution.direction()) ? BigDecimal.valueOf(plan.maxBuyPrice()) : null));
                    }
                }

                // Private event realization and the next price are sampled only AFTER all decisions have been made.
                boolean effective = event && random.nextDouble() < .75;
                if (effective) internalFundamentals = FundamentalEngine.applyEvent(internalFundamentals,
                        type, up ? "UP" : "DOWN", "MEDIUM");
                var fundamental = FundamentalEngine.priceContribution(internalFundamentals, new BigDecimal("100"), price,
                        new BigDecimal("0.35"), stageIndex == 1 && roundNo % 10 == 0);
                var external = MarketPriceEngine.price(new MarketPriceEngine.Context(price, closes, marketState, 3,
                        drift, effective ? (up ? 1 : -1) * random.nextDouble(.3, 1.6) : 0, 0, 8, stageIndex == 0,
                        random.nextLong(), fundamental.fundamentalImpactPct().doubleValue(), fundamental.reportSurprisePct().doubleValue()));
                var auction = AuctionClearingEngine.clear(external.price(), orders, LIQUIDITY, IMPACT, FEE, SLIP);
                price = auction.clearingPrice();
                for (var fill : auction.fills()) {
                    Actor actor = actors.stream().filter(value -> value.key.equals(fill.participantKey())).findFirst().orElseThrow();
                    if (fill.filledShares() == 0) continue;
                    if ("BUY".equals(fill.side())) {
                        actor.averageCost = (actor.averageCost * actor.shares + fill.grossAmount().add(fill.feeAmount()).doubleValue())
                                / (actor.shares + fill.filledShares());
                        actor.shares += fill.filledShares();
                        actor.cash = actor.cash.subtract(fill.grossAmount()).subtract(fill.feeAmount());
                        actor.lastBuyRound = roundNo;
                        roundBuys += fill.filledShares(); largestBuy = Math.max(largestBuy, fill.filledShares());
                    } else {
                        actor.shares -= fill.filledShares();
                        actor.cash = actor.cash.add(fill.grossAmount()).subtract(fill.feeAmount());
                        if (actor.shares == 0) actor.averageCost = 0;
                        roundSells += fill.filledShares();
                    }
                    if (actor.cash.signum() < 0 || actor.shares < 0) throw new IllegalStateException("invalid portfolio");
                    actor.turnover = actor.turnover.add(fill.grossAmount());
                    actor.lastTradeRound = roundNo; actor.lastSide = fill.side(); actor.trades++;
                }
                if (stageIndex == 0) round.setOpenPrice(price);
            }
            round.setClosePrice(price);
            history.add(round); if (history.size() > 50) history.remove(0);
            closes.add(price); if (closes.size() > 51) closes.remove(0);
            marketState = MarketStateCalculator.calculate(closes, 3, marketState);
            publicBuys = roundBuys; publicSells = roundSells;
            buyConcentration = roundBuys == 0 ? 0 : largestBuy / (double) roundBuys;
            if (roundNo % 10 == 0) {
                publishedFair = FundamentalEngine.metrics(internalFundamentals).fairValueIndex().doubleValue();
                reportRound = roundNo;
            }
            for (Actor actor : actors) {
                double assets = actor.assets(price).doubleValue(); actor.peak = Math.max(actor.peak, assets);
                actor.drawdown = Math.min(actor.drawdown, (assets / actor.peak - 1) * 100);
            }
        }
        Actor human = actors.get(8);
        double humanAssets = human.assets(price).doubleValue();
        List<Actor> ais = actors.subList(0, 8);
        final BigDecimal mark = price;
        double best = ais.stream().mapToDouble(actor -> actor.assets(mark).doubleValue()).max().orElse(0);
        double worst = ais.stream().mapToDouble(actor -> actor.assets(mark).doubleValue()).min().orElse(0);
        return new GameResult(best > humanAssets, humanAssets < worst,
                ais.stream().mapToDouble(actor -> actor.assets(mark).doubleValue() / 100 - 100).average().orElse(0),
                best / 100 - 100, humanAssets / 100 - 100,
                ais.stream().mapToDouble(actor -> actor.drawdown).average().orElse(0),
                ais.stream().mapToInt(actor -> actor.trades).sum(), reviews, probes, accumulations, exits,
                ais.stream().filter(actor -> actor.assets(mark).doubleValue() > humanAssets).count() * 100d / 8);
    }

    private static double baselineTarget(Baseline baseline, AiMarketSnapshot snapshot, Actor actor, int totalRounds) {
        return switch (baseline) {
            case CASH -> 0;
            case BUY_AND_HOLD -> 100;
            case DCA -> Math.max(actor.exposure(snapshot.currentPrice()), Math.min(100, snapshot.roundNo() * 100d / (totalRounds / 2)));
            case TREND -> snapshot.return3Pct() > 1 && snapshot.return8Pct() > 0 ? 90 : snapshot.return3Pct() < -1 ? 10 : actor.exposure(snapshot.currentPrice());
            case CONTRARIAN -> snapshot.return3Pct() < -2 ? 75 : snapshot.return3Pct() > 2 ? 20 : actor.exposure(snapshot.currentPrice());
            case EVENT -> snapshot.hasPublicEvent() ? "UP".equals(snapshot.eventDirection()) ? 90 : 10 : actor.exposure(snapshot.currentPrice());
        };
    }

    private static final class Actor {
        final String key, style;
        BigDecimal cash = INITIAL, roundStartAssets = INITIAL, turnover = BigDecimal.ZERO;
        long shares;
        double averageCost, drawdown, peak = INITIAL.doubleValue();
        Integer lastTradeRound, lastBuyRound;
        String lastSide;
        int trades;
        AiStrategyPlanner.State strategy;
        Actor(String key, String style) { this.key = key; this.style = style; }
        BigDecimal assets(BigDecimal price) { return cash.add(price.multiply(BigDecimal.valueOf(shares))); }
        double exposure(BigDecimal price) { return shares * price.doubleValue() / Math.max(.01, assets(price).doubleValue()) * 100; }
    }

    public enum Baseline { CASH, BUY_AND_HOLD, DCA, TREND, CONTRARIAN, EVENT }
    private record GameResult(boolean aiFirst, boolean humanLast, double averageAiReturn, double bestAiReturn,
                              double humanReturn, double averageAiDrawdown, int trades, int reviews,
                              int probes, int accumulations, int exits, double aiBeatHumanPct) {}
    public record Report(int games, int rounds, String difficulty, Baseline baseline, double aiFirstPct,
                         double humanLastPct, double averageAiReturnPct, double bestAiReturnPct,
                         double humanReturnPct, double averageAiDrawdownPct, double averageAiTrades,
                         double averageModelReviewOpportunities, int probes, int accumulations, int exits,
                         double individualAiBeatHumanPct) {}
    public record LeagueReport(int games, int rounds, double normalOverEasyPct, double hardOverNormalPct,
                               double easyMeanReturnPct, double normalMeanReturnPct, double hardMeanReturnPct) {}
}
