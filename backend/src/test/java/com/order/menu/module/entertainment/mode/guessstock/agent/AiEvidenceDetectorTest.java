package com.order.menu.module.entertainment.mode.guessstock.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.order.menu.module.entertainment.mode.guessstock.entity.AiInvestmentThesis;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiEvidenceDetectorTest {
    private final AiEvidenceDetector detector = new AiEvidenceDetector();
    private final AiThesisConditionEvaluator.ValidationResult valid =
            new AiThesisConditionEvaluator.ValidationResult(false, List.of());

    @Test
    void reviewAndExpiryAreMeasuredFromTheirOwnLifecycleAnchors() {
        AiInvestmentThesis thesis = thesis(2, 4, 3, 4);
        AiEvidenceDetector.EvidenceResult result = detector.detect(thesis, snapshot(7), valid);

        assertTrue(has(result, "THESIS_REVIEW_DUE"));
        assertFalse(has(result, "THESIS_EXPIRED"));

        thesis.setRenewedRound(2);
        result = detector.detect(thesis, snapshot(7), valid);
        assertTrue(has(result, "THESIS_EXPIRED"));
    }

    @Test
    void activeThesisWithoutMaterialChangeDoesNotForceAnotherDecision() {
        AiInvestmentThesis thesis = thesis(6, 5, 3, 8);
        AiEvidenceDetector.EvidenceResult result = detector.detect(thesis, snapshot(7), valid);

        assertFalse(result.materialChange());
    }

    @Test
    void drawdownEvidenceOnlyFiresWhenEnteringANewBand() {
        AiInvestmentThesis thesis = thesis(7, 7, 5, 8);

        AiEvidenceDetector.EvidenceResult crossed = detector.detect(thesis,
                snapshot(8, List.of("100", "99", "98", "93"), -7), valid);
        AiEvidenceDetector.EvidenceResult remained = detector.detect(thesis,
                snapshot(9, List.of("100", "98", "93", "92"), -8), valid);

        assertTrue(has(crossed, "DRAWDOWN_BAND_CROSSED"));
        assertFalse(has(remained, "DRAWDOWN_BAND_CROSSED"));
    }

    private static boolean has(AiEvidenceDetector.EvidenceResult result, String type) {
        return result.evidences().stream().anyMatch(item -> type.equals(item.type()));
    }

    private static AiInvestmentThesis thesis(int lastValidated, int renewed, int review, int expiry) {
        AiInvestmentThesis thesis = new AiInvestmentThesis();
        thesis.setCreatedRound(1);
        thesis.setLastValidatedRound(lastValidated);
        thesis.setRenewedRound(renewed);
        thesis.setReviewAfterRounds(review);
        thesis.setExpireAfterRounds(expiry);
        return thesis;
    }

    private static AiMarketSnapshot snapshot(int round) {
        return snapshot(round, List.of("30", "30", "30"), 0);
    }

    private static AiMarketSnapshot snapshot(int round, List<String> prices, double drawdown) {
        List<BigDecimal> values = prices.stream().map(AiEvidenceDetectorTest::bd).toList();
        BigDecimal current = values.get(values.size() - 1);
        return new AiMarketSnapshot(round, 5, "CLOSING", current, current,
                values, 0, 0, 0,
                0, 0, 0, 0, 0, 0, bd("30"), bd("30"), 0, 0, 0,
                0, 3, "无事件", "无", null, null, null, null, null, null, 0, null,
                "SIDEWAYS", "SIDEWAYS", false, drawdown, drawdown, "SIDEWAYS", "NORMAL",
                3, 3, 0, PublicEventDigest.none());
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
