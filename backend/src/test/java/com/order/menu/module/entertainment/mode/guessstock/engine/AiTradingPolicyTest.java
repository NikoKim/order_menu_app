package com.order.menu.module.entertainment.mode.guessstock.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiTradingPolicyTest {
    @Test
    void avoidsChasingAnOverheatedOpening() {
        AiTradingPolicy.Decision decision = decide("TREND", "OPENING", "112", "0",
                List.of("100", "104", "108"), new AiTradingPolicy.Memory(2, 1, 0), 2, 6, false);

        assertEquals("HOLD", decision.action());
        assertTrue(decision.reason().contains("追高"));
    }

    @Test
    void avoidsCatchingAnAcceleratingFall() {
        AiTradingPolicy.Decision decision = decide("CONTRARIAN", "OPENING", "80", "0",
                List.of("100", "95", "88"), new AiTradingPolicy.Memory(1, 1, 0), 5, 6, false);

        assertEquals("HOLD", decision.action());
        assertTrue(decision.reason().contains("等待企稳"));
    }

    @Test
    void contrarianCanBuyAfterADeclineStartsStabilizing() {
        AiTradingPolicy.Decision decision = decide("CONTRARIAN", "OPENING", "90", "0",
                List.of("105", "97", "92"), new AiTradingPolicy.Memory(0, 0, 0), 4, 6, false);

        assertEquals("BUY", decision.action());
        assertTrue(decision.riskFraction() > 0);
    }

    @Test
    void sellsProfitablePositionWhenTrendTurnsWeak() {
        AiTradingPolicy.Decision decision = decide("TREND", "CLOSING", "106", "94",
                List.of("98", "104", "110"), new AiTradingPolicy.Memory(2, 0, 1), 1, 6, false);

        assertEquals("SELL", decision.action());
        assertTrue(decision.reason().contains("持仓收益"));
    }

    @Test
    void publicEventTextProducesSentimentWithoutHiddenOutcome() {
        assertTrue(AiTradingPolicy.publicEventSentiment("订单增长", "需求超预期并追加采购") > 0);
        assertTrue(AiTradingPolicy.publicEventSentiment("出口收紧", "客户削减订单并出现供应中断") < 0);
    }

    @Test
    void structuredEventInformationChangesAiSignalWeight() {
        double rumor = AiTradingPolicy.eventInformationMultiplier("LOW", "RUMOR", "EXPECTED", "WEAK");
        double confirmed = AiTradingPolicy.eventInformationMultiplier("HIGH", "CONFIRMED", "MAJOR", "STRONG");

        assertTrue(confirmed > rumor);
    }

    private static AiTradingPolicy.Decision decide(String style, String stage, String price, String cost,
                                                    List<String> closes, AiTradingPolicy.Memory memory,
                                                    int rank, int participants, boolean revision) {
        List<AiTradingPolicy.MarketPoint> history = closes.stream().map(value ->
                new AiTradingPolicy.MarketPoint(new BigDecimal(value), new BigDecimal(value))).toList();
        return AiTradingPolicy.decide(new AiTradingPolicy.Context(style, "HARD", 7, 12, stage,
                new BigDecimal(price), new BigDecimal(cost), new BigDecimal("10000"), 100,
                history, 100, 80, rank, participants, "无事件", "无", 3, 1000,
                memory, 42, revision));
    }
}
