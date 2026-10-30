package com.order.menu.module.entertainment.mode.guessstock.agent;

import static org.junit.jupiter.api.Assertions.*;
import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockTrade;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiPublicOpponentModelTest {
    @Test
    void publicSettledFlowProducesStableProfilesRatherThanGuessingPrivateHoldings() {
        var a = trade(1, 1, "BUY", 90);
        var b = trade(2, 1, "BUY", 10);
        var c = trade(1, 2, "SELL", 20);
        var first = AiPublicOpponentModel.summarize(List.of(a, b, c));
        assertEquals(first, AiPublicOpponentModel.summarize(List.of(c, b, a)));
        assertEquals(.9, first.buyConcentration());
        assertEquals(1, first.sellConcentration());
        assertEquals(List.of(1L, 70L, 2L), first.activeActors().get(0));
        assertEquals(0, first.flowPersistence());
    }

    private static GuessStockTrade trade(long participant, int round, String side, long quantity) {
        var trade = new GuessStockTrade(); trade.setParticipantId(participant);
        trade.setRoundNo(round); trade.setSide(side); trade.setQuantity(quantity); return trade;
    }
}
