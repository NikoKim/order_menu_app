package com.order.menu.module.entertainment.mode.guessstock.agent;

import com.order.menu.module.entertainment.mode.guessstock.engine.AiTradingPolicy;
import com.order.menu.module.entertainment.mode.guessstock.entity.AiInvestmentThesis;
import com.order.menu.module.entertainment.mode.guessstock.service.ModelAiDecisionService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiTradingFallbackPolicy {
    public ModelAiDecisionService.Decision decide(AiInvestmentThesis thesis, boolean invalidated,
                                                  AiTradingPolicy.Context context, double currentPositionPct,
                                                  double maxPositionPct, String fallbackReason) {
        if (thesis != null && !invalidated) {
            return new ModelAiDecisionService.Decision("KEEP", thesis.getTargetPositionPct().doubleValue(), .9,
                    "LOW", thesis.getMarketRegime(), thesis.getDirection(), thesis.getTimeHorizon(),
                    List.of("FALLBACK_KEEP_ACTIVE_THESIS"), List.of(), thesis.getReviewAfterRounds(),
                    thesis.getExpireAfterRounds(), fallbackReason + "；沿用未失效的活动观点");
        }
        AiTradingPolicy.Decision policy = AiTradingPolicy.decide(context);
        double target = currentPositionPct;
        if ("BUY".equals(policy.action())) target += (maxPositionPct - currentPositionPct) * policy.riskFraction();
        else if ("SELL".equals(policy.action())) target *= 1 - policy.riskFraction();
        String type = thesis == null ? "CREATE_THESIS" : "REPLACE_THESIS";
        String direction = target > currentPositionPct + .01 ? "BULLISH"
                : target < currentPositionPct - .01 ? "BEARISH" : "NEUTRAL";
        return new ModelAiDecisionService.Decision(type, Math.max(0, Math.min(maxPositionPct, target)),
                policy.confidence(), "MEDIUM", policy.regime().name(), direction, "SHORT",
                List.of("LOCAL_FALLBACK"), List.of(), 1, 3, fallbackReason + "；" + policy.reason());
    }
}
