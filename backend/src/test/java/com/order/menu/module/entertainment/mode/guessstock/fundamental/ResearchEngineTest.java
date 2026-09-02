package com.order.menu.module.entertainment.mode.guessstock.fundamental;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class ResearchEngineTest {
    @Test
    void researchIsDeterministicNoisyAndConsumesSharedBudget() {
        var account = new ResearchEngine.Account(3, 1);
        var result = ResearchEngine.perform(account, ResearchEngine.Action.SURVEY_DEMAND,
                new FundamentalEngine.State(120, 100, 100, 100, 100), "CONFIRMED", 0, 5L);
        assertEquals(0, result.account().remainingPoints());
        assertEquals(result, ResearchEngine.perform(account, ResearchEngine.Action.SURVEY_DEMAND,
                new FundamentalEngine.State(120, 100, 100, 100, 100), "CONFIRMED", 0, 5L));
        assertThrows(IllegalArgumentException.class, () -> ResearchEngine.perform(result.account(),
                ResearchEngine.Action.SURVEY_DEMAND, FundamentalEngine.neutral(), "RUMOR", 0, 6L));
    }
}
