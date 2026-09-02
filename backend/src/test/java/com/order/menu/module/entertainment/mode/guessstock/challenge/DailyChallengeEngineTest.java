package com.order.menu.module.entertainment.mode.guessstock.challenge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DailyChallengeEngineTest {
    @Test
    void sameDayAndVersionProduceSameSeed() {
        var first = DailyChallengeEngine.create(LocalDate.of(2026, 8, 31), 9, "V47");
        assertEquals(first, DailyChallengeEngine.create(LocalDate.of(2026, 8, 31), 9, "V47"));
        assertNotEquals(first.scenarioSeed(),
                DailyChallengeEngine.create(LocalDate.of(2026, 9, 1), 9, "V47").scenarioSeed());
    }
}
