package com.order.menu.module.entertainment.mode.guessstock.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.order.menu.module.entertainment.mode.guessstock.config.GuessStockConfig;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GuessStockEventSelectorTest {
    @Test
    void aLargePoolDoesNotStackOccurrenceProbabilities() {
        List<GuessStockConfig.EventConfig> events = new ArrayList<>();
        for (int i = 0; i < 30; i++) events.add(event("事件" + i, "10"));

        int occurred = 0;
        for (long seed = 1; seed <= 10_000; seed++) {
            if (GuessStockEventSelector.select(events, Set.of(), seed) != null) occurred++;
        }

        assertTrue(occurred > 850 && occurred < 1150,
                "30 个 10% 事件的整体频率应接近 10%，实际为 " + occurred / 100.0 + "%");
    }

    @Test
    void anOccurredEventCannotBeSelectedAgain() {
        List<GuessStockConfig.EventConfig> events = List.of(event("事件一", "100"), event("事件二", "100"));
        Set<Integer> used = new HashSet<>();
        used.add(0);

        for (long seed = 1; seed <= 100; seed++) {
            assertTrue(GuessStockEventSelector.select(events, used, seed) == 1);
        }
    }

    @Test
    void candidateShuffleDoesNotDependOnUploadOrder() {
        List<GuessStockConfig.EventConfig> original = List.of(
                event("事件甲", "100"), event("事件乙", "100"), event("事件丙", "100"));
        List<GuessStockConfig.EventConfig> reordered = List.of(original.get(2), original.get(0), original.get(1));

        for (long seed = 1; seed <= 100; seed++) {
            String originalName = original.get(GuessStockEventSelector.select(original, Set.of(), seed)).name();
            String reorderedName = reordered.get(GuessStockEventSelector.select(reordered, Set.of(), seed)).name();
            assertEquals(originalName, reorderedName);
        }
    }

    @Test
    void forcedOccurrenceStillSelectsOnlyOneUnusedEvent() {
        List<GuessStockConfig.EventConfig> events = List.of(event("事件一", "0"), event("事件二", "0"));
        Integer selected = GuessStockEventSelector.select(events, Set.of(), 42, true);

        assertTrue(selected != null && (selected == 0 || selected == 1));
    }

    @Test
    void candidateWeightChangesSelectionWithoutChangingOccurrenceMeaning() {
        var light = new GuessStockConfig.EventConfig("轻权重", "事件介绍", new BigDecimal("100"),
                new BigDecimal("50"), "RANDOM", BigDecimal.ONE, new BigDecimal("3"),
                "INDUSTRY", "MEDIUM", "CONFIRMED", "NORMAL", "MEDIUM", true, BigDecimal.ONE);
        var heavy = new GuessStockConfig.EventConfig("高权重", "事件介绍", new BigDecimal("100"),
                new BigDecimal("50"), "RANDOM", BigDecimal.ONE, new BigDecimal("3"),
                "INDUSTRY", "MEDIUM", "CONFIRMED", "NORMAL", "MEDIUM", true, new BigDecimal("9"));
        int heavySelections = 0;
        for (long seed = 1; seed <= 5000; seed++) {
            if (GuessStockEventSelector.select(List.of(light, heavy), Set.of(), seed) == 1) heavySelections++;
        }
        assertTrue(heavySelections > 4200, "9:1 权重应显著偏向高权重事件");
    }

    private static GuessStockConfig.EventConfig event(String name, String probability) {
        return new GuessStockConfig.EventConfig(name, "事件介绍", new BigDecimal(probability),
                new BigDecimal("50"), "RANDOM", BigDecimal.ONE, new BigDecimal("3"), true);
    }
}
