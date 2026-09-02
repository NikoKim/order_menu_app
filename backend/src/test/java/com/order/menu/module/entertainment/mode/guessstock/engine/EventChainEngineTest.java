package com.order.menu.module.entertainment.mode.guessstock.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import com.order.menu.module.entertainment.mode.guessstock.config.GuessStockConfig;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventChainEngineTest {
    @Test
    void followUpRespectsDepthAndIsDeterministic() {
        var source = event("需求下滑", 1, List.of(), List.of("ORDER"), 100);
        var next = event("订单减少", 2, List.of("ORDER"), List.of(), 100);
        var state = new EventChainEngine.ChainState("DEMAND_CHAIN", 1, 1, 0);
        assertEquals(next, EventChainEngine.next(source, List.of(next), state, 8L).event());
        assertEquals(EventChainEngine.next(source, List.of(next), state, 8L),
                EventChainEngine.next(source, List.of(next), state, 8L));
        assertNull(EventChainEngine.next(source, List.of(next),
                new EventChainEngine.ChainState("DEMAND_CHAIN", 4, 4, 0), 8L));
    }

    private static GuessStockConfig.EventConfig event(String name, int step, List<String> parents,
                                                       List<String> follows, double probability) {
        return new GuessStockConfig.EventConfig(name, "测试", BigDecimal.valueOf(probability), BigDecimal.valueOf(100),
                "DOWN", BigDecimal.ONE, new BigDecimal("3"), "DEMAND", "HIGH", "CONFIRMED", "NORMAL",
                "MEDIUM", true, BigDecimal.ONE, "DEMAND_CHAIN", step, parents, follows,
                new BigDecimal("100"), 4);
    }
}
