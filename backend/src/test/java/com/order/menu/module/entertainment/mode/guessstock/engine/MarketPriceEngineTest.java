package com.order.menu.module.entertainment.mode.guessstock.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketPriceEngineTest {
    @Test
    void sameSeedIsDeterministicAndComponentsReconcile() {
        List<BigDecimal> prices = List.of(bd("100"), bd("99"), bd("97"), bd("96"));
        var state = MarketStateCalculator.calculate(prices, 3, null);
        var context = new MarketPriceEngine.Context(bd("96"), prices, state, 3,
                0, -1.2, .3, 12, true, 99L);

        var first = MarketPriceEngine.price(context);
        var second = MarketPriceEngine.price(context);
        assertEquals(first, second);
        BigDecimal sum = first.baseDriftPct().add(first.regimeImpactPct()).add(first.momentumImpactPct())
                .add(first.eventImpactPct()).add(first.meanReversionImpactPct())
                .add(first.noiseImpactPct()).add(first.orderFlowImpactPct())
                .add(first.fundamentalImpactPct()).add(first.reportSurprisePct());
        assertEquals(0, sum.compareTo(first.rawTotalImpactPct()));
        assertEquals(0, first.rawTotalImpactPct().add(first.clampAdjustmentPct())
                .compareTo(first.totalImpactPct()));
        assertEquals(0, first.regimeImpactPct().compareTo(BigDecimal.ZERO));
        assertTrue(first.price().signum() > 0);
    }

    @Test
    void longTrendHasLessMomentumThanFreshTrend() {
        var fresh = state(1, 1);
        var tired = state(12, 12);
        var freshResult = MarketPriceEngine.price(context(fresh));
        var tiredResult = MarketPriceEngine.price(context(tired));
        assertTrue(freshResult.momentumImpactPct().abs().compareTo(tiredResult.momentumImpactPct().abs()) > 0);
    }

    private static MarketPriceEngine.Context context(MarketStateCalculator.MarketState state) {
        return new MarketPriceEngine.Context(bd("100"), List.of(bd("97"), bd("98"), bd("100")),
                state, 3, 0, 0, 0, 12, true, 1L);
    }
    private static MarketStateCalculator.MarketState state(int age, int consecutive) {
        return new MarketStateCalculator.MarketState(MarketStateCalculator.TrendState.UP,
                MarketStateCalculator.VolatilityState.NORMAL, age, age, consecutive,
                .8, 2, 3, 10, .5, .3, 1);
    }
    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
