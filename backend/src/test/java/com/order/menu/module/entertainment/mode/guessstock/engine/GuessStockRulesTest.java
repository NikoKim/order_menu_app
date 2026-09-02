package com.order.menu.module.entertainment.mode.guessstock.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.order.menu.common.exception.BusinessException;
import com.order.menu.module.entertainment.mode.guessstock.config.GuessStockConfig;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class GuessStockRulesTest {
    @Test
    void validatesAndNormalizesConfig() {
        GuessStockConfig normalized = GuessStockRules.validate(config(List.of(event("大单签约", "40", "70", "UP", "3", "8"))));

        assertEquals("AERO", normalized.stockCode());
        assertEquals("UP", normalized.events().get(0).direction());
        assertEquals(4, normalized.aiCount());
    }

    @Test
    void acceptsIndependentEventProbabilitiesWhoseTotalExceedsOneHundred() {
        GuessStockConfig normalized = GuessStockRules.validate(config(List.of(
                event("事件一", "60", "100", "UP", "1", "2"),
                event("事件二", "50", "100", "DOWN", "1", "2"))));

        assertEquals(2, normalized.events().size());
    }

    @Test
    void acceptsAnUnboundedNumberOfEvents() {
        List<GuessStockConfig.EventConfig> events = IntStream.range(0, 200)
                .mapToObj(index -> event("事件" + index, "10", "50", "RANDOM", "1", "3"))
                .toList();

        assertEquals(200, GuessStockRules.validate(config(events)).events().size());
    }

    @Test
    void acceptsMoreThanEightAiPlayers() {
        GuessStockConfig manyAi = new GuessStockConfig("AERO", "航天", 10, 1, 5,
                new BigDecimal("10000"), new BigDecimal("100"), new BigDecimal("3"), "MEDIUM",
                new BigDecimal("10"), "AUTO", true, 8, true, 12, "NORMAL",
                List.of("TREND", "CONSERVATIVE"), List.of());

        assertEquals(12, GuessStockRules.validate(manyAi).aiPlayers().size());
    }

    @Test
    void rejectsAnIndividualEventProbabilityAboveOneHundred() {
        GuessStockConfig invalid = config(List.of(event("事件一", "101", "100", "UP", "1", "2")));

        BusinessException error = assertThrows(BusinessException.class, () -> GuessStockRules.validate(invalid));
        assertTrue(error.getMessage().contains("事件发生概率"));
    }

    @Test
    void openingUsesBuyQuantityLiquidityAndEvent() {
        GuessStockRules.StagePricingResult result = GuessStockRules.openingPrice(
                new BigDecimal("100"), 1500, new BigDecimal("1"), new BigDecimal("2"),
                new BigDecimal("1000"), 3, new BigDecimal("10"));

        assertEquals(new BigDecimal("107.9191"), result.price());
        assertEquals(new BigDecimal("4.6212"), result.orderFlowChangePercent());
    }

    @Test
    void closingUsesBaseEventAndNegativeSellImpact() {
        GuessStockRules.StagePricingResult result = GuessStockRules.closingPrice(
                new BigDecimal("100"), 600, new BigDecimal("1"), new BigDecimal("2"),
                BigDecimal.ZERO, new BigDecimal("1000"), 3, new BigDecimal("10"));

        assertEquals(new BigDecimal("101.0315"), result.price());
        assertEquals(new BigDecimal("-1.9738"), result.orderFlowChangePercent());
    }

    @Test
    void normalLiquidityDefaultsToOneThousandShares() {
        assertEquals(new BigDecimal("1000.0000"), GuessStockRules.validate(config(List.of())).marketLiquidityShares());
        assertEquals(1_000L, GuessStockRules.validate(config(List.of())).maxBuyQuantity());
    }

    @Test
    void normalizesIndependentAiCapitalAndBehaviorProfiles() {
        GuessStockConfig base = config(List.of());
        GuessStockConfig custom = new GuessStockConfig(base.stockCode(), base.stockType(), base.roundCount(),
                base.revealSeconds(), base.tradingSeconds(), base.initialCash(), base.initialPrice(),
                base.baseVolatilityPercent(), base.liquidity(), new BigDecimal("2500"),
                base.maxOrderImpactPercent(), 1000L, base.progressionMode(), base.multiplayerEnabled(),
                base.maxHumanPlayers(), true, 2, "HARD", List.of("TREND", "CONTRARIAN"), List.of(
                new GuessStockConfig.AiPlayerConfig("小额趋势", "TREND", new BigDecimal("5000"),
                        80L, new BigDecimal("75"), new BigDecimal("45"), "分批跟随趋势"),
                new GuessStockConfig.AiPlayerConfig("逆势大户", "CONTRARIAN", new BigDecimal("80000"),
                        3000L, new BigDecimal("35"), new BigDecimal("60"), "考虑自身订单冲击")
        ), List.of());

        GuessStockConfig normalized = GuessStockRules.validate(custom);

        assertEquals(2, normalized.aiPlayers().size());
        assertEquals(new BigDecimal("5000.0000"), normalized.aiPlayers().get(0).initialCash());
        assertEquals(3000L, normalized.aiPlayers().get(1).maxOrderQuantity());
        assertEquals("考虑自身订单冲击", normalized.aiPlayers().get(1).strategyPrompt());
    }

    @Test
    void buyLimitUsesAvailableCashAndPreviousClose() {
        assertEquals(9L, GuessStockRules.maxBuyQuantity(new BigDecimal("999.99"), new BigDecimal("100")));
        assertEquals(0L, GuessStockRules.maxBuyQuantity(new BigDecimal("-0.01"), new BigDecimal("100")));
    }

    @Test
    void logarithmicReturnsAreSymmetricAndStayPositive() {
        GuessStockRules.StagePricingResult up = GuessStockRules.openingPrice(new BigDecimal("100"), 0,
                new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("1000"), 1, new BigDecimal("10"));
        GuessStockRules.StagePricingResult down = GuessStockRules.openingPrice(new BigDecimal("100"), 0,
                new BigDecimal("-10"), BigDecimal.ZERO, new BigDecimal("1000"), 1, new BigDecimal("10"));

        assertTrue(up.price().multiply(down.price()).subtract(new BigDecimal("10000")).abs()
                .compareTo(new BigDecimal("0.01")) < 0);
        assertTrue(down.price().signum() > 0);
    }

    @Test
    void meanReversionPullsOpeningPriceTowardPreviousClose() {
        assertTrue(GuessStockRules.meanReversionPercent(new BigDecimal("120"), new BigDecimal("100"),
                new BigDecimal("3")).signum() < 0);
        assertTrue(GuessStockRules.meanReversionPercent(new BigDecimal("80"), new BigDecimal("100"),
                new BigDecimal("3")).signum() > 0);
    }

    private static GuessStockConfig config(List<GuessStockConfig.EventConfig> events) {
        return new GuessStockConfig(" aero ", "航天", 10, 1, 5,
                new BigDecimal("10000"), new BigDecimal("100"), new BigDecimal("3"), "MEDIUM",
                new BigDecimal("10"), "AUTO", true, 8, true, 4, "NORMAL",
                List.of("TREND", "CONSERVATIVE"), events);
    }

    private static GuessStockConfig.EventConfig event(String name, String occurrence, String effect,
                                                       String direction, String min, String max) {
        return new GuessStockConfig.EventConfig(name, "事件介绍", new BigDecimal(occurrence),
                new BigDecimal(effect), direction, new BigDecimal(min), new BigDecimal(max), true);
    }
}
