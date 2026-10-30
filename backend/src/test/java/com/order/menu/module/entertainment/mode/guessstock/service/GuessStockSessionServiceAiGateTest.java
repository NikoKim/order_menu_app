package com.order.menu.module.entertainment.mode.guessstock.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.order.menu.module.entertainment.core.entity.GameSession;
import com.order.menu.module.entertainment.core.mapper.GameSessionMapper;
import com.order.menu.module.entertainment.mode.guessstock.mapper.GuessStockAiTaskMapper;
import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockAiTask;
import com.order.menu.module.entertainment.mode.guessstock.entity.AiInvestmentThesis;
import com.order.menu.module.entertainment.mode.guessstock.agent.*;
import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GuessStockSessionServiceAiGateTest {
    @Mock GuessStockAiTaskMapper tasks;
    @Mock GameSessionMapper sessions;
    @InjectMocks GuessStockSessionService service;

    @Test
    void automaticDeadlineDoesNotDiscardAnInFlightPaidReview() {
        GameSession session = session();
        session.setPhaseEndsAt(LocalDateTime.now().minusSeconds(1));
        when(tasks.selectCount(any())).thenReturn(1L);
        ReflectionTestUtils.invokeMethod(service, "progressLocked", session);
        assertEquals("OPENING_TRADING", session.getPhase());
        verifyNoInteractions(sessions);
    }

    @Test
    void manualConfirmationDefersClearingWithoutHoldingTheRequestOpenForHttp() {
        GameSession session = session();
        when(tasks.selectCount(any())).thenReturn(1L);
        ReflectionTestUtils.invokeMethod(service, "progressFromGate", session, LocalDateTime.now());
        assertNotNull(session.getPhaseEndsAt());
        assertEquals("OPENING_TRADING", session.getPhase());
        verify(sessions).updateById(session);
    }

    @Test
    void clearingCannotExecuteATaskAlreadyOwnedByABackgroundWorker() {
        GuessStockAiTask task = new GuessStockAiTask(); task.setDecisionStatus("PROCESSING");
        when(tasks.selectById(1L)).thenReturn(task);
        ReflectionTestUtils.invokeMethod(service, "executeAiTaskLocked", 1L, LocalDateTime.now(), false);
        verifyNoInteractions(sessions);
        verify(tasks, never()).claim(anyLong());
    }

    @Test
    void keepingAnUnfinishedExitDoesNotDowngradeItsUrgency() {
        var thesis = new AiInvestmentThesis(); thesis.setTargetPositionPct(BigDecimal.ZERO);
        thesis.setConfidence(new BigDecimal("0.8")); thesis.setDirection("BEARISH");
        thesis.setTimeHorizon("SHORT"); thesis.setMarketRegime("DOWN");
        thesis.setReviewAfterRounds(8); thesis.setExpireAfterRounds(24);
        var state = new AiStrategyPlanner.State("EXIT", 1, 1, 100, 90, 90, 105, 1, 100, 0, 0, 0);
        var plan = new AiStrategyPlanner.Plan(state, 0, "HIGH", true, false, .95, "EXIT_PLAN_IN_PROGRESS", 0, 110, 1, 0);
        ModelAiDecisionService.Decision decision = ReflectionTestUtils.invokeMethod(service, "localStrategyDecision", thesis,
                new AiThesisConditionEvaluator.ValidationResult(false, List.of()), plan, mock(AiMarketSnapshot.class));
        assertNotNull(decision);
        assertEquals("KEEP", decision.decisionType());
        assertEquals("HIGH", decision.urgency());
    }

    private static GameSession session() {
        GameSession session = new GameSession(); session.setId(1L); session.setCurrentRound(1);
        session.setStatus("ACTIVE"); session.setPhase("OPENING_TRADING"); return session;
    }
}
