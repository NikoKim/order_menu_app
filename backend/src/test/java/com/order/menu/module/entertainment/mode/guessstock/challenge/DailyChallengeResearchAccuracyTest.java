package com.order.menu.module.entertainment.mode.guessstock.challenge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockResearchAction;
import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockRound;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DailyChallengeResearchAccuracyTest {
    @Test
    void resolvesFundamentalAndEventResearchAgainstRoundTruth() {
        GuessStockRound round = new GuessStockRound();
        round.setDemandIndex(new BigDecimal("106"));
        round.setFundamentalScore(new BigDecimal("94"));
        round.setEventCredibility("PRELIMINARY");

        assertEquals("UP", direction("SURVEY_DEMAND", "OPENING", round));
        assertEquals("DOWN", direction("PREVIEW_REPORT", "OPENING", round));
        assertEquals("UP", direction("VERIFY_EVENT", "OPENING", round));
    }

    @Test
    void closingLiquidityUsesTheAlreadyPublishedOpeningImbalance() {
        GuessStockRound round = new GuessStockRound();
        round.setOpeningOrderFlowChangePercent(new BigDecimal("9"));
        assertEquals("FLAT", direction("CHECK_LIQUIDITY", "OPENING", round));
        assertEquals("UP", direction("CHECK_LIQUIDITY", "CLOSING", round));
    }

    private static String direction(String type, String stage, GuessStockRound round) {
        GuessStockResearchAction action = new GuessStockResearchAction();
        action.setActionType(type);
        action.setStage(stage);
        return DailyChallengeService.actualDirection(action, round);
    }
}
