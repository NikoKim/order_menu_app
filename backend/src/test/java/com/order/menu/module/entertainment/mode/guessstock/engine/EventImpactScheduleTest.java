package com.order.menu.module.entertainment.mode.guessstock.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class EventImpactScheduleTest {
    @Test
    void accidentIsFrontLoadedAndWeightsSumToOne() {
        var slices = EventImpactSchedule.create("ACCIDENT", 4, "OPENING",
                new BigDecimal("-10"), 1, 1);

        assertEquals(3, slices.size());
        assertEquals("CLOSING", slices.get(1).targetStage());
        assertEquals(4, slices.get(1).targetRound());
        assertEquals("OPENING", slices.get(2).targetStage());
        assertEquals(5, slices.get(2).targetRound());
        assertEquals(0, slices.stream().map(EventImpactSchedule.Slice::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(BigDecimal.ONE));
        assertTrue(slices.get(0).impactPct().abs().compareTo(slices.get(1).impactPct().abs()) > 0);
    }

    @Test
    void saturationAndContrarianFactorsApplyToTotal() {
        var slices = EventImpactSchedule.create("FINANCIAL", 1, "CLOSING",
                new BigDecimal("8"), .5, 1.25);
        BigDecimal total = slices.stream().map(EventImpactSchedule.Slice::impactPct)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, total.compareTo(new BigDecimal("5.0000")));
    }
}
