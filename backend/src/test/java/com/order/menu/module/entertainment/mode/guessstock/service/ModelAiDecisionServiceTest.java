package com.order.menu.module.entertainment.mode.guessstock.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ModelAiDecisionServiceTest {
    private final ModelAiDecisionService service = new ModelAiDecisionService(new ObjectMapper());

    @Test
    void parsesPlainAndFencedStructuredDecisionsAndClampsTarget() {
        ModelAiDecisionService.Decision plain = service.parse(
                "{\"decision_type\":\"CREATE_THESIS\",\"market_regime\":\"筑底\",\"direction\":\"BULLISH\",\"urgency\":\"MEDIUM\",\"target_position_pct\":140,\"confidence\":0.8,\"time_horizon\":\"medium\",\"research_action\":\"SURVEY_DEMAND\",\"reason_codes\":[\"卖压减弱\"],\"invalid_conditions\":[{\"type\":\"PRICE_BELOW\",\"value\":28.8}],\"review_after_rounds\":2,\"expire_after_rounds\":6,\"reason\":\"趋势增强\"}");
        ModelAiDecisionService.Decision fenced = service.parse(
                "```json\n{\"decision_type\":\"ADJUST\",\"market_regime\":\"过热\",\"direction\":\"BULLISH\",\"urgency\":\"LOW\",\"target_position_pct\":25,\"confidence\":0.7,\"time_horizon\":\"SHORT\",\"review_after_rounds\":1,\"expire_after_rounds\":3,\"reason\":\"控制仓位\"}\n```");

        assertEquals("CREATE_THESIS", plain.decisionType());
        assertEquals(140, plain.targetPositionPct());
        assertEquals("筑底", plain.marketRegime());
        assertEquals("PRICE_BELOW", plain.invalidConditions().get(0).type());
        assertEquals("SURVEY_DEMAND", plain.researchAction());
        assertEquals("ADJUST", fenced.decisionType());
        assertEquals(25, fenced.targetPositionPct());
    }

    @Test
    void invalidActionMissingTargetAndMalformedJsonFailValidation() {
        assertThrows(ModelAiDecisionService.ModelDecisionException.class, () -> service.parse(
                "{\"decision_type\":\"TRADE\",\"target_position_pct\":50,\"reason\":\"不合法动作\"}"));
        assertThrows(ModelAiDecisionService.ModelDecisionException.class, () -> service.parse(
                "{\"decision_type\":\"KEEP\",\"reason\":\"缺少目标仓位\"}"));
        assertThrows(ModelAiDecisionService.ModelDecisionException.class, () -> service.parse(
                "{\"decision_type\":\"CREATE_THESIS\",\"target_position_pct\":50,\"invalid_conditions\":[\"跌破前低\"]}"));
        assertThrows(ModelAiDecisionService.ModelDecisionException.class, () -> service.parse(
                "{\"decision_type\":\"CREATE_THESIS\",\"target_position_pct\":50,\"invalid_conditions\":[{\"type\":\"PRICE_BELOW\"}]}"));
        assertThrows(ModelAiDecisionService.ModelDecisionException.class, () -> service.parse(
                "{\"decision_type\":\"CREATE_THESIS\",\"target_position_pct\":50,\"invalid_conditions\":[{\"type\":\"EVENT_DIRECTION\",\"direction\":\"MAYBE\"}]}"));
        assertThrows(ModelAiDecisionService.ModelDecisionException.class, () -> service.parse("not-json"));
    }

    @Test
    void promptIncludesDecisionRelevantMarketContext() {
        String prompt = service.buildPrompt(new ModelAiDecisionService.Context(
                "趋势", "HARD", 6, 12, "OPENING", "108.50", "101.20", "3500.00", 24,
                java.util.List.of(new java.math.BigDecimal("103"), new java.math.BigDecimal("108.5")),
                80, 130, 3, 5, "订单增长", "需求超预期", "DEMAND", "UP", "STRONG", true,
                "12000.00", "65", "45", "趋势减弱时缩小仓位", 18.5,
                1.2, 3.4, -2.1, 1.8, 2.1, .4, .7, -.2, .3, -6.3,
                "112.00", "96.00", "UP", "SIDEWAYS", true, 2, 1, 1,
                "BUY 15股", "{\"id\":\"T3\",\"target\":25}", "[]", "[\"PRICE_DEVIATION\"]",
                "5", 1, "2", "30", 12, .4, .5,
                -3.1, -8.2, "UP", "NORMAL", 4, 2, 3, "HIGH", 0, true,
                "GROWTH_BANK", "5000", "40", 54.2, "NORMAL", "2500", 9));

        assertTrue(prompt.contains("\"round\":6"));
        assertTrue(prompt.contains("\"phase\":\"OPENING\""));
        assertTrue(prompt.contains("\"cost\":101.20"));
        assertTrue(prompt.contains("\"closes\":[103,108.5]"));
        assertTrue(prompt.contains("\"regime\":\"UP\""));
        assertTrue(prompt.contains("\"regimeChanged\":true"));
        assertTrue(prompt.contains("\"trendAge\":4"));
        assertTrue(prompt.contains("\"residualSignal\":\"HIGH\""));
        assertTrue(prompt.contains("\"remainingStages\":0"));
        assertTrue(!prompt.contains("currentImpact"));
        assertTrue(!prompt.contains("remainingImpact"));
        assertTrue(prompt.contains("\"isNew\":true"));
        assertTrue(prompt.contains("\"pending\":\"BUY 15股\""));
        assertTrue(prompt.contains("最终股票按收盘价折算"));
        assertTrue(prompt.contains("DecisionPacket="));
        assertTrue(prompt.contains("target_exposure_pct"));
        assertTrue(prompt.contains("\"product\":\"GROWTH_BANK\""));
        assertTrue(!prompt.contains("最近最多20回合客观行情"));
    }

    @Test
    void eachStyleHasHumanLikePriceDiscipline() {
        assertTrue(service.styleGuidance("CONSERVATIVE").contains("重视本金"));
        assertTrue(service.styleGuidance("CONTRARIAN").contains("过度反应"));
        assertTrue(service.styleGuidance("AGGRESSIVE").contains("失效条件"));
        assertTrue(service.styleGuidance("RANDOM").contains("连贯观点"));
    }
}
