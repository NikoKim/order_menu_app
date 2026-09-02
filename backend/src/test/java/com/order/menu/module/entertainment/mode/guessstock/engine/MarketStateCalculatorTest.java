package com.order.menu.module.entertainment.mode.guessstock.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketStateCalculatorTest {
    @Test
    void hysteresisPreventsSmallBounceFromFlippingDowntrend() {
        var down = MarketStateCalculator.calculate(prices("100", "99", "97", "95"), 3, null);
        var bounce = MarketStateCalculator.calculate(prices("100", "99", "97", "95", "95.2"), 3, down);

        assertTrue(down.trend() == MarketStateCalculator.TrendState.DOWN
                || down.trend() == MarketStateCalculator.TrendState.STRONG_DOWN);
        assertTrue(bounce.trend() == MarketStateCalculator.TrendState.DOWN
                || bounce.trend() == MarketStateCalculator.TrendState.STRONG_DOWN);
        assertEquals(down.trendAge() + 1, bounce.trendAge());
    }

    @Test
    void sameInputsProduceSameState() {
        List<BigDecimal> prices = prices("100", "101", "100.5", "102");
        assertEquals(MarketStateCalculator.calculate(prices, 3, null),
                MarketStateCalculator.calculate(prices, 3, null));
    }

    @Test
    void volatilityUsesExpectedRealizedScaleAndCoolsOneTierAtATime() {
        var extreme = MarketStateCalculator.calculate(
                prices("100", "104", "96", "105", "95"), 3, null);
        assertEquals(MarketStateCalculator.VolatilityState.EXTREME, extreme.volatility());
        assertTrue(extreme.normalizedVolatility() >= 1.85);

        var calm = MarketStateCalculator.calculate(
                prices("100", "100.1", "100.2", "100.1", "100.2"), 3, extreme);
        assertEquals(MarketStateCalculator.VolatilityState.HIGH, calm.volatility());
    }

    private static List<BigDecimal> prices(String... values) {
        return java.util.Arrays.stream(values).map(BigDecimal::new).toList();
    }
}
