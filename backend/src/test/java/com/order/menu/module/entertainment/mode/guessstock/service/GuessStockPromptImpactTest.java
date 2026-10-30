package com.order.menu.module.entertainment.mode.guessstock.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import com.order.menu.module.entertainment.mode.guessstock.config.GuessStockConfig;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class GuessStockPromptImpactTest {
    private GuessStockConfig market() {
        var config = mock(GuessStockConfig.class);
        when(config.marketLiquidityShares()).thenReturn(new BigDecimal("2000"));
        when(config.maxOrderImpactPercent()).thenReturn(new BigDecimal("20"));
        return config;
    }

    @Test
    void promptUsesExecutableStepNotConfiguredMaximumOrderSize() {
        double impact = GuessStockSessionService.estimatedImpactPercent(219996, new BigDecimal("15000"),
                new BigDecimal("32.6"), new BigDecimal("2"), market(), 9);
        assertEquals(460d / 18000 * 20, impact, .000001);
        assertEquals(2, GuessStockSessionService.estimatedImpactPercent(219996, new BigDecimal("300000"),
                new BigDecimal("32.6"), new BigDecimal("2"), market(), 9));
    }

    @Test
    void exhaustedTurnoverOrZeroImpactBudgetDoesNotInventExecutableImpact() {
        assertEquals(0, GuessStockSessionService.estimatedImpactPercent(219996, BigDecimal.ZERO,
                new BigDecimal("32.6"), new BigDecimal("2"), market(), 9));
        assertEquals(0, GuessStockSessionService.estimatedImpactPercent(219996, new BigDecimal("15000"),
                new BigDecimal("32.6"), BigDecimal.ZERO, market(), 9));
    }
}
