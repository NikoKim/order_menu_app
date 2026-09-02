package com.order.menu.module.entertainment.mode.guessstock.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.order.menu.module.entertainment.mode.guessstock.entity.AiInvestmentThesis;
import com.order.menu.module.entertainment.mode.guessstock.service.ModelAiDecisionService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiDecisionValidatorTest {
    private final AiDecisionValidator validator = new AiDecisionValidator();
    private final AiThesisConditionEvaluator.ValidationResult valid =
            new AiThesisConditionEvaluator.ValidationResult(false, List.of());
    private final AiEvidenceDetector.EvidenceResult noEvidence =
            new AiEvidenceDetector.EvidenceResult(false, false, List.of());

    @Test
    void keepCannotModifyTargetAndAdjustCannotFlipDirection() {
        assertThrows(ModelAiDecisionService.ModelDecisionException.class,
                () -> validator.validate(decision("KEEP", 25, "BULLISH"), thesis(), valid, noEvidence));
        assertThrows(ModelAiDecisionService.ModelDecisionException.class,
                () -> validator.validate(decision("ADJUST", 25, "BEARISH"), thesis(), valid, noEvidence));
    }

    @Test
    void validKeepPreservesThesisTarget() {
        ModelAiDecisionService.Decision result = validator.validate(
                decision("KEEP", 40, "BULLISH"), thesis(), valid, noEvidence);
        assertEquals(40, result.targetPositionPct());
    }

    @Test
    void triggeredInvalidConditionRequiresReplacingOrClosingThesis() {
        var invalid = new AiThesisConditionEvaluator.ValidationResult(true,
                List.of(new AiThesisConditionEvaluator.TriggeredCondition("PRICE_BELOW", bd("29"), bd("28"))));
        assertThrows(ModelAiDecisionService.ModelDecisionException.class,
                () -> validator.validate(decision("ADJUST", 20, "BULLISH"), thesis(), invalid,
                        new AiEvidenceDetector.EvidenceResult(true, false, List.of())));
        assertEquals("REPLACE_THESIS", validator.validate(decision("REPLACE_THESIS", 10, "BEARISH"),
                thesis(), invalid, new AiEvidenceDetector.EvidenceResult(true, false, List.of())).decisionType());
    }

    private static AiInvestmentThesis thesis() {
        AiInvestmentThesis thesis = new AiInvestmentThesis();
        thesis.setTargetPositionPct(bd("40"));
        thesis.setMarketRegime("趋势");
        thesis.setDirection("BULLISH");
        thesis.setTimeHorizon("MEDIUM");
        thesis.setReviewAfterRounds(2);
        thesis.setExpireAfterRounds(6);
        return thesis;
    }

    private static ModelAiDecisionService.Decision decision(String type, double target, String direction) {
        return new ModelAiDecisionService.Decision(type, target, .7, "MEDIUM", "趋势", direction,
                "MEDIUM", List.of(), List.of(), 2, 6, "测试");
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
