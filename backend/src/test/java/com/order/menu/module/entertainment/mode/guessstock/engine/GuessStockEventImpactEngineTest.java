package com.order.menu.module.entertainment.mode.guessstock.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.order.menu.module.entertainment.mode.guessstock.config.GuessStockConfig;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuessStockEventImpactEngineTest {

    @Test
    void sameContextAndSeedProduceSameResult() {
        GuessStockEventImpactEngine.Context context = context(event("MEDIUM", "CONFIRMED", "NORMAL", "MEDIUM"),
                new BigDecimal("100"), List.of(new BigDecimal("98"), new BigDecimal("99")), 21L);

        assertEquals(GuessStockEventImpactEngine.evaluate(context), GuessStockEventImpactEngine.evaluate(context));
    }

    @Test
    void marketStateAmplifiesOversoldGoodNewsAndAbsorbsOverheatedGoodNews() {
        GuessStockConfig.EventConfig event = event("MEDIUM", "CONFIRMED", "NORMAL", "MEDIUM");
        var overheated = GuessStockEventImpactEngine.evaluate(context(event, new BigDecimal("110"),
                List.of(new BigDecimal("90"), new BigDecimal("92"), new BigDecimal("95")), 42L));
        var oversold = GuessStockEventImpactEngine.evaluate(context(event, new BigDecimal("90"),
                List.of(new BigDecimal("110"), new BigDecimal("108"), new BigDecimal("105")), 42L));

        assertEquals("OVERHEATED", overheated.regime());
        assertEquals("OVERSOLD", oversold.regime());
        assertTrue(oversold.finalChangePercent().compareTo(overheated.finalChangePercent()) > 0);
    }

    @Test
    void strongerMoreCredibleAndSurprisingEventHasLargerImpact() {
        var weak = GuessStockEventImpactEngine.evaluate(context(
                event("LOW", "RUMOR", "EXPECTED", "WEAK"), new BigDecimal("100"), List.of(), 71L));
        var strong = GuessStockEventImpactEngine.evaluate(context(
                event("HIGH", "CONFIRMED", "MAJOR", "STRONG"), new BigDecimal("100"), List.of(), 71L));

        assertTrue(strong.finalChangePercent().compareTo(weak.finalChangePercent()) > 0);
    }

    @Test
    void zeroEffectProbabilityProducesNoObservableImpact() {
        GuessStockConfig.EventConfig event = new GuessStockConfig.EventConfig("需求变化", "测试", BigDecimal.TEN,
                BigDecimal.ZERO, "UP", BigDecimal.ONE, new BigDecimal("4"), "DEMAND", "HIGH",
                "CONFIRMED", "MAJOR", "STRONG", true);
        var result = GuessStockEventImpactEngine.evaluate(context(event, new BigDecimal("100"), List.of(), 1L));

        assertEquals("NONE", result.tier());
        assertEquals(0, result.finalChangePercent().signum());
        assertTrue(!result.effective());
    }

    private static GuessStockEventImpactEngine.Context context(GuessStockConfig.EventConfig event,
                                                                BigDecimal reference, List<BigDecimal> prices,
                                                                long seed) {
        return new GuessStockEventImpactEngine.Context(event, null, new BigDecimal("0.2"),
                new BigDecimal("10"), reference, prices, seed);
    }

    private static GuessStockConfig.EventConfig event(String relevance, String credibility,
                                                       String surprise, String strength) {
        return new GuessStockConfig.EventConfig("需求增长", "需求超出预期", BigDecimal.TEN,
                new BigDecimal("100"), "UP", new BigDecimal("4"), new BigDecimal("4"),
                "DEMAND", relevance, credibility, surprise, strength, true);
    }
}
