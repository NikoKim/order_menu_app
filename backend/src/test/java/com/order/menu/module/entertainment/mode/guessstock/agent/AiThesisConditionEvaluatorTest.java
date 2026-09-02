package com.order.menu.module.entertainment.mode.guessstock.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.menu.module.entertainment.mode.guessstock.entity.AiInvestmentThesis;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiThesisConditionEvaluatorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiThesisConditionEvaluator evaluator = new AiThesisConditionEvaluator(objectMapper);

    @Test
    void priceAndReturnConditionsAreVerifiedByServerFacts() throws Exception {
        AiInvestmentThesis thesis = thesis("[ {\"type\":\"PRICE_BELOW\",\"value\":29.0},"
                + "{\"type\":\"RETURN_BELOW\",\"windowRounds\":3,\"valuePct\":-6} ]");
        AiThesisConditionEvaluator.ValidationResult result = evaluator.evaluate(thesis, snapshot());

        assertTrue(result.invalidated());
        assertTrue(result.triggeredConditions().stream().anyMatch(item -> "PRICE_BELOW".equals(item.type())));
        assertFalse(result.triggeredConditions().stream().anyMatch(item -> "RETURN_BELOW".equals(item.type())));
    }

    @Test
    void thesisAgeConditionCannotBeOverruledByModelText() {
        AiInvestmentThesis thesis = thesis("[{\"type\":\"THESIS_AGE_ABOVE\",\"value\":2}]");
        assertTrue(evaluator.evaluate(thesis, snapshot()).invalidated());
    }

    private static AiInvestmentThesis thesis(String conditions) {
        AiInvestmentThesis thesis = new AiInvestmentThesis();
        thesis.setCreatedRound(1);
        thesis.setInvalidConditionsJson(conditions);
        return thesis;
    }

    private static AiMarketSnapshot snapshot() {
        return new AiMarketSnapshot(5, 5, "CLOSING", bd("28.8"), bd("30"),
                List.of(bd("30"), bd("29.5"), bd("29"), bd("28.8")), -4, -4, -4,
                1.2, 1.2, -.2, -.2, -.2, .1, bd("30"), bd("28.8"), 0, 0, 0,
                -4, 3, "无事件", "无", null, null, null, null, null, null, 0, null,
                "DOWN", "SIDEWAYS", true, -4, -4, "DOWN", "NORMAL",
                2, 1, -3, PublicEventDigest.none());
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
