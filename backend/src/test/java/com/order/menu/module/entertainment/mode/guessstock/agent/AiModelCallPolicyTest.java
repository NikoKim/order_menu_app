package com.order.menu.module.entertainment.mode.guessstock.agent;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AiModelCallPolicyTest {
    private final AiModelSettings settings = new AiModelSettings();

    @Test
    void eightAgentsDoNotCallForBothStagesOfEveryRound() {
        int calls = 0;
        for (int ai = 0; ai < 8; ai++) {
            AiStrategyPlanner.State state = null;
            for (int round = 1; round <= 180; round++) {
                var market = AiStrategyPlannerTest.context(round, "TREND", 30, 30, 100, "SAFE", 0, .02,
                        100, 100, 100, 100, 100, 100, 100, 100).market();
                for (int stage = 0; stage < 2; stage++) {
                    if (AiModelCallPolicy.shouldCall(state, market, false, false, false, settings)) {
                        calls++;
                        state = (state == null ? AiStrategyPlanner.State.empty(100) : state).withModel(round, 80);
                    }
                }
            }
        }
        assertEquals(64, calls);
        assertTrue(calls < 180 * 8 * 2 / 20);
    }

    @Test
    void urgentFactsAreRecheckedButNotRepeatedWithinTheSameRound() {
        var market = AiStrategyPlannerTest.context(9, "TREND", 30, 30, 100, "SAFE", 0, .02,
                100, 100, 100, 100, 100, 100, 100, 100).market();
        var state = AiStrategyPlanner.State.empty(100).withModel(1, 80);
        assertTrue(AiModelCallPolicy.shouldCall(state, market, true, false, false, settings));
        assertFalse(AiModelCallPolicy.shouldCall(state, market, false, true, false, settings));
        assertFalse(AiModelCallPolicy.shouldCall(state.withModel(9, 80), market, true, true, true, settings));
    }

    @Test
    void cacheCostEstimateForProUsesCachedInputOnlyOnce() {
        var rates = AiModelCostPolicy.rates("https://api.deepseek.com/v1", "deepseek-v4-pro", settings);
        assertTrue(rates.officialPrice());
        // A planning scenario, not a measured live-provider result: 64 calls, 1800 hit + 800 miss + 100 output.
        double yuan = rates.cost(2600, 1800, 100).doubleValue() * 64;
        assertEquals(.66816, yuan, .000001);
        assertTrue(yuan >= .5 && yuan <= 1);
        assertFalse(AiModelCostPolicy.rates("https://api.deepseek.com.evil.example", "deepseek-v4-pro", settings).officialPrice());
    }
}
