package com.order.menu.module.entertainment.mode.guessstock.agent;

import com.order.menu.module.entertainment.mode.guessstock.entity.AiInvestmentThesis;
import com.order.menu.module.entertainment.mode.guessstock.service.ModelAiDecisionService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiDecisionValidator {
    public ModelAiDecisionService.Decision validate(ModelAiDecisionService.Decision decision,
                                                     AiInvestmentThesis thesis,
                                                     AiThesisConditionEvaluator.ValidationResult validation,
                                                     AiEvidenceDetector.EvidenceResult evidence) {
        String type = decision.decisionType();
        if (thesis == null && !"CREATE_THESIS".equals(type)) {
            throw new ModelAiDecisionService.ModelDecisionException("没有活动观点时必须创建新观点");
        }
        if (thesis != null && "CREATE_THESIS".equals(type)) {
            throw new ModelAiDecisionService.ModelDecisionException("已有活动观点时不能重复创建观点");
        }
        if (thesis != null && validation.invalidated()
                && !List.of("REPLACE_THESIS", "CLOSE_THESIS").contains(type)) {
            throw new ModelAiDecisionService.ModelDecisionException("结构化失效条件已触发，必须替换或关闭旧观点");
        }
        if ("KEEP".equals(type)) {
            if (Math.abs(decision.targetPositionPct() - thesis.getTargetPositionPct().doubleValue()) > .01) {
                throw new ModelAiDecisionService.ModelDecisionException("KEEP 不能修改目标仓位");
            }
            return new ModelAiDecisionService.Decision("KEEP", thesis.getTargetPositionPct().doubleValue(),
                    decision.confidence(), decision.urgency(), thesis.getMarketRegime(), thesis.getDirection(),
                    thesis.getTimeHorizon(), readOnly(decision.reasonCodes()), List.of(), thesis.getReviewAfterRounds(),
                    thesis.getExpireAfterRounds(), decision.reason(), decision.researchAction());
        }
        if ("ADJUST".equals(type) && !thesis.getDirection().equals(decision.direction())) {
            throw new ModelAiDecisionService.ModelDecisionException("ADJUST 不能改变观点方向");
        }
        if (List.of("REPLACE_THESIS", "CLOSE_THESIS").contains(type)
                && !validation.invalidated() && !evidence.materialChange()) {
            throw new ModelAiDecisionService.ModelDecisionException("没有重要新证据时不能替换或关闭观点");
        }
        if ("CLOSE_THESIS".equals(type) && decision.targetPositionPct() > .01) {
            throw new ModelAiDecisionService.ModelDecisionException("关闭观点时目标仓位必须为 0");
        }
        return decision;
    }

    private static <T> List<T> readOnly(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
