package com.order.menu.module.entertainment.mode.guessstock.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockRound;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublicDecisionSnapshotTest {
    @Test
    void serializedContractContainsOnlyCategoricalEventResidual() throws Exception {
        GuessStockRound round = new GuessStockRound(); round.setRoundNo(1); round.setClosePrice(new BigDecimal("100"));
        var snapshot = new AiMarketSnapshotService().create(1, 10, "OPENING", new BigDecimal("100"),
                new BigDecimal("100"), List.of(), round, 0, 0, 3,
                new PublicEventDigest(PublicEventDigest.ResidualSignal.HIGH, 0));
        String json = new ObjectMapper().writeValueAsString(snapshot);
        assertTrue(json.contains("residualSignal"));
        assertFalse(json.contains("currentEventImpact"));
        assertFalse(json.contains("remainingEventImpact"));
    }
}
