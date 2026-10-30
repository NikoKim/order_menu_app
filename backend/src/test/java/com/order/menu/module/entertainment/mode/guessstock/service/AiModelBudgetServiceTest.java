package com.order.menu.module.entertainment.mode.guessstock.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.order.menu.module.entertainment.mode.guessstock.agent.AiModelCostPolicy;
import com.order.menu.module.entertainment.mode.guessstock.agent.AiModelSettings;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AiModelBudgetServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AiModelSettings settings = new AiModelSettings();
    private final AiModelBudgetService budgets = new AiModelBudgetService(jdbc, settings);
    private final AiModelCostPolicy.Rates rates = AiModelCostPolicy.rates("https://api.deepseek.com", "deepseek-v4-pro", settings);

    @Test
    void reservationUsesAtomicSpentAndReservedBudgetPredicate() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        var reservation = budgets.reserve(1, 180, 8, rates, 8000);
        assertEquals(rates.cost(8000, 0, 384), reservation.amount());
        verify(jdbc).update(contains("ON DUPLICATE KEY UPDATE session_id = session_id"), eq(1L));
        verify(jdbc).update(contains("spent_yuan + reserved_yuan + ? <= ?"),
                eq(reservation.amount()), eq(1L), eq(reservation.amount()), eq(new BigDecimal("1.00")), eq(96));
    }

    @Test
    void exhaustedBudgetRejectsBeforeAnyHttpRequest() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        assertThrows(ModelAiDecisionService.ModelDecisionException.class,
                () -> budgets.reserve(1, 180, 8, rates, 8000));
    }

    @Test
    void actualCacheUsageReleasesUnusedReservation() {
        var reservation = new AiModelBudgetService.Reservation(1, rates.cost(8000, 0, 384), rates);
        var usage = new ModelAiDecisionService.InvocationTelemetry(100, 2600, 100, 200, 1, 1800, 800);
        assertEquals(rates.cost(2600, 1800, 100), budgets.settle(reservation, usage));
        // A timeout can still be billed by the provider; conservatively retain its reservation as estimated spend.
        assertEquals(reservation.amount(), budgets.settle(reservation, null));
    }
}
