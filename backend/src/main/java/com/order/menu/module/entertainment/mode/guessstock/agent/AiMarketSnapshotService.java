package com.order.menu.module.entertainment.mode.guessstock.agent;

import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockRound;
import com.order.menu.module.entertainment.mode.guessstock.engine.MarketStateCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiMarketSnapshotService {
    public AiMarketSnapshot create(int roundNo, int totalRounds, String stage, BigDecimal currentPrice,
                                   BigDecimal previousClose, List<GuessStockRound> history,
                                   GuessStockRound round, long publicBuyQuantity, long publicSellQuantity,
                                   double baseVolatilityPct) {
        return create(roundNo, totalRounds, stage, currentPrice, previousClose, history, round,
                publicBuyQuantity, publicSellQuantity, baseVolatilityPct, PublicEventDigest.none());
    }

    public AiMarketSnapshot create(int roundNo, int totalRounds, String stage, BigDecimal currentPrice,
                                   BigDecimal previousClose, List<GuessStockRound> history,
                                   GuessStockRound round, long publicBuyQuantity, long publicSellQuantity,
                                   double baseVolatilityPct, PublicEventDigest publicEventDigest) {
        List<BigDecimal> prices = history.stream().map(GuessStockRound::getClosePrice)
                .filter(price -> price != null && price.signum() > 0).collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        boolean appendedCurrent = prices.isEmpty() || prices.get(prices.size() - 1).compareTo(currentPrice) != 0;
        if (appendedCurrent) prices.add(currentPrice);
        BigDecimal high = prices.stream().max(BigDecimal::compareTo).orElse(currentPrice);
        BigDecimal low = prices.stream().min(BigDecimal::compareTo).orElse(currentPrice);
        long totalFlow = publicBuyQuantity + publicSellQuantity;
        double imbalance = totalFlow == 0 ? 0 : (publicBuyQuantity - publicSellQuantity) * 100d / totalFlow;
        boolean openingEvent = "OPENING".equals(round.getEventStage());
        boolean stageEvent = "OPENING".equals(stage)
                ? Integer.valueOf(1).equals(round.getOpeningEventOccurred())
                : Integer.valueOf(1).equals(round.getClosingEventOccurred());
        List<BigDecimal> previousPrices = prices.size() <= 1
                ? List.of() : prices.subList(0, prices.size() - 1);
        MarketStateCalculator.MarketState persisted = persistedState(history.isEmpty()
                ? null : history.get(history.size() - 1), appendedCurrent ? previousPrices : prices, baseVolatilityPct);
        MarketStateCalculator.MarketState state = history.isEmpty()
                ? MarketStateCalculator.calculate(prices, baseVolatilityPct, null)
                : appendedCurrent ? MarketStateCalculator.calculate(prices, baseVolatilityPct, persisted) : persisted;
        String regime = regime(state);
        String previousRegime = appendedCurrent ? regime(persisted) : regime;
        boolean regimeChanged = appendedCurrent && previousPrices.size() >= 3 && !regime.equals(previousRegime);
        return new AiMarketSnapshot(roundNo, Math.max(0, totalRounds - roundNo), stage,
                currentPrice, previousClose, List.copyOf(prices), periodReturn(prices, 3), periodReturn(prices, 8),
                periodReturn(prices, 20), volatility(prices, 8), volatility(prices, 20), slope(prices, 3),
                slope(prices, 8), slope(prices, 20), acceleration(prices), high, low,
                publicBuyQuantity, publicSellQuantity, imbalance, percentChange(currentPrice, previousClose),
                baseVolatilityPct, stageEvent ? round.getEventName() : "无事件",
                stageEvent ? round.getEventDescription() : "无", stageEvent ? round.getEventDirection() : null,
                stageEvent ? round.getEventType() : null, stageEvent ? round.getEventRelevance() : null,
                stageEvent ? round.getEventCredibility() : null, stageEvent ? round.getEventSurprise() : null,
                stageEvent ? round.getEventStrength() : null, stageEvent ? roundNo : 0,
                stageEvent ? (openingEvent ? "OPENING" : "CLOSING") : null,
                regime, previousRegime, regimeChanged, percentChange(currentPrice, high), maxDrawdown(prices),
                state.trend().name(), state.volatility().name(), state.trendAge(), state.regimeAge(),
                state.consecutiveDirectionRounds(), publicEventDigest == null
                        ? PublicEventDigest.none() : publicEventDigest);
    }

    private static MarketStateCalculator.MarketState persistedState(GuessStockRound round,
                                                                     List<BigDecimal> prices,
                                                                     double baseVolatilityPct) {
        if (round == null || round.getTrendState() == null || round.getVolatilityState() == null) {
            return MarketStateCalculator.calculate(prices, baseVolatilityPct, null);
        }
        try {
            return new MarketStateCalculator.MarketState(
                    MarketStateCalculator.TrendState.valueOf(round.getTrendState()),
                    MarketStateCalculator.VolatilityState.valueOf(round.getVolatilityState()),
                    round.getRegimeAge() == null ? 1 : round.getRegimeAge(),
                    round.getTrendAge() == null ? 1 : round.getTrendAge(),
                    round.getConsecutiveDirectionRounds() == null ? 0 : round.getConsecutiveDirectionRounds(),
                    round.getTrendScore() == null ? 0 : round.getTrendScore().doubleValue(),
                    round.getCurrentVolatilityPercent() == null ? baseVolatilityPct : round.getCurrentVolatilityPercent().doubleValue(),
                    MarketStateCalculator.periodReturn(prices, 3), MarketStateCalculator.periodReturn(prices, 20),
                    MarketStateCalculator.slope(prices, 3), MarketStateCalculator.slope(prices, 8),
                    MarketStateCalculator.volatility(prices, 8));
        } catch (IllegalArgumentException ignored) {
            return MarketStateCalculator.calculate(prices, baseVolatilityPct, null);
        }
    }

    private static double maxDrawdown(List<BigDecimal> prices) {
        double high = 0;
        double maximum = 0;
        for (BigDecimal price : prices) {
            high = Math.max(high, price.doubleValue());
            if (high > 0) maximum = Math.min(maximum, (price.doubleValue() / high - 1) * 100);
        }
        return maximum;
    }

    private static String regime(MarketStateCalculator.MarketState state) {
        if (state.volatility() == MarketStateCalculator.VolatilityState.EXTREME) return "EXTREME_VOLATILITY";
        return state.trend().name();
    }

    private static double periodReturn(List<BigDecimal> prices, int period) {
        if (prices.size() < 2) return 0;
        int start = Math.max(0, prices.size() - 1 - period);
        return percentChange(prices.get(prices.size() - 1), prices.get(start));
    }

    private static double volatility(List<BigDecimal> prices, int period) {
        return MarketStateCalculator.volatility(prices, period);
    }

    private static double slope(List<BigDecimal> prices, int period) {
        return MarketStateCalculator.slope(prices, period);
    }

    private static double acceleration(List<BigDecimal> prices) {
        if (prices.size() < 4) return 0;
        double latest = percentChange(prices.get(prices.size() - 1), prices.get(prices.size() - 2));
        double prior = percentChange(prices.get(prices.size() - 2), prices.get(prices.size() - 3));
        return latest - prior;
    }

    private static double percentChange(BigDecimal current, BigDecimal baseline) {
        if (current == null || baseline == null || baseline.signum() == 0) return 0;
        return current.subtract(baseline).divide(baseline, 10, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100")).doubleValue();
    }
}
