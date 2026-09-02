package com.order.menu.module.entertainment.mode.guessstock.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class GuessStockSessionServiceStageEventTest {
    @Test
    void closingStageDrawsAgainWhenOpeningStageHadNoEvent() {
        assertTrue(GuessStockSessionService.shouldDrawClosingEvent(false, null));
    }

    @Test
    void closingStageDoesNotDrawTwiceOrReplaceAnOpeningEvent() {
        assertFalse(GuessStockSessionService.shouldDrawClosingEvent(true, null));
        assertFalse(GuessStockSessionService.shouldDrawClosingEvent(false, 0));
        assertFalse(GuessStockSessionService.shouldDrawClosingEvent(false, 1));
    }

    @Test
    void targetPositionIsConvertedToTargetShares() {
        assertTrue(GuessStockSessionService.targetShares(new BigDecimal("10000"),
                new BigDecimal("25"), 40) == 160);
        assertTrue(GuessStockSessionService.targetShares(new BigDecimal("10000"),
                new BigDecimal("25"), 0) == 0);
    }

    @Test
    void creditPhaseIsInsertedOnlyWhenLoansAreEnabled() {
        assertTrue(GuessStockSessionService.shouldOpenCreditPhase(true));
        assertFalse(GuessStockSessionService.shouldOpenCreditPhase(false));
        assertFalse(GuessStockSessionService.shouldOpenCreditPhase(null));
        assertTrue(GuessStockSessionService.shouldOpenCreditPhase(true, "PHASED"));
        assertFalse(GuessStockSessionService.shouldOpenCreditPhase(true, "INLINE"));
    }

    @Test
    void inlineRepaymentGateOnlyOpensForDueRiskOrFinalRound() {
        assertFalse(GuessStockSessionService.needsInlineRepaymentAttention(false, 5, 8, "SAFE"));
        assertTrue(GuessStockSessionService.needsInlineRepaymentAttention(false, 8, 8, "SAFE"));
        assertTrue(GuessStockSessionService.needsInlineRepaymentAttention(false, 5, 8, "WARNING"));
        assertTrue(GuessStockSessionService.needsInlineRepaymentAttention(true, 10, 12, "SAFE"));
    }
}
