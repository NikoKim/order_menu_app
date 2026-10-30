package com.order.menu.module.entertainment.mode.guessstock.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.order.menu.module.entertainment.mode.guessstock.credit.service.GuessStockCreditService;
import com.order.menu.module.entertainment.mode.guessstock.engine.AuctionClearingEngine;
import com.order.menu.module.entertainment.mode.guessstock.entity.*;
import com.order.menu.module.entertainment.mode.guessstock.mapper.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GuessStockSessionServiceExecutionTest {
    @Mock GuessStockParticipantMapper participants;
    @Mock GuessStockPositionMapper positions;
    @Mock GuessStockOrderMapper orders;
    @Mock GuessStockTradeMapper trades;
    @Mock AiInvestmentDecisionMapper decisions;
    @Mock GuessStockCreditService credit;
    @InjectMocks GuessStockSessionService service;
    GuessStockParticipant participant;
    GuessStockPosition position;
    GuessStockOrder order;
    AiInvestmentDecision audit;

    @BeforeEach
    void setup() {
        participant = new GuessStockParticipant(); participant.setId(2L); participant.setParticipantType("AI");
        participant.setCashBalance(new BigDecimal("1000")); participant.setReservedCash(BigDecimal.ZERO);
        position = new GuessStockPosition(); position.setQuantity(10L); position.setAverageCost(new BigDecimal("100"));
        position.setReservedQuantity(0L);
        order = new GuessStockOrder(); order.setId(3L); order.setSessionId(1L); order.setParticipantId(2L);
        order.setRoundNo(1); order.setStage("CLOSING"); order.setSide("TARGET");
        audit = new AiInvestmentDecision(); audit.setPlannedShares(50L); audit.setExecutionStatus("PLANNED");
        when(participants.selectByIdForUpdate(2L)).thenReturn(participant);
        when(positions.selectByParticipantForUpdate(2L)).thenReturn(position);
        when(credit.debt(1L, 2L)).thenReturn(BigDecimal.ZERO);
        when(decisions.selectList(any())).thenReturn(List.of(audit));
    }

    @Test
    void partialFillAuditUsesActualSharesAndActualFeeAdjustedExposure() {
        var fill = new AuctionClearingEngine.Fill("2", "BUY", 50, 3, new BigDecimal("110"),
                new BigDecimal("330"), new BigDecimal("0.33"), BigDecimal.ZERO);
        ReflectionTestUtils.invokeMethod(service, "fillAuctionOrder", order, fill);
        assertEquals(3L, audit.getExecutedShares());
        assertEquals(new BigDecimal("330"), audit.getExecutedValue());
        assertEquals("PARTIALLY_FILLED", audit.getExecutionStatus());
        assertEquals(new BigDecimal("669.6700"), participant.getCashBalance());
        assertEquals(13L, position.getQuantity());
        assertTrue(audit.getResultingPositionPct().doubleValue() > 68);
        verify(trades).insert(any(GuessStockTrade.class));
    }

    @Test
    void unfilledOrderNeverAppearsAsAnExecutedTrade() {
        var fill = new AuctionClearingEngine.Fill("2", "BUY", 50, 0, new BigDecimal("110"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        ReflectionTestUtils.invokeMethod(service, "fillAuctionOrder", order, fill);
        assertEquals(0L, audit.getExecutedShares());
        assertEquals("HOLD", audit.getExecutedDirection());
        assertEquals("UNFILLED", audit.getExecutionStatus());
        assertEquals(10L, position.getQuantity());
        verifyNoInteractions(trades);
    }
}
