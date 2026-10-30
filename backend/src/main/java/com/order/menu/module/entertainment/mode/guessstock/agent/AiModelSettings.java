package com.order.menu.module.entertainment.mode.guessstock.agent;

import java.math.BigDecimal;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class AiModelSettings {
    @Value("${entertainment.guess-stock.ai.session-budget-yuan:1.00}")
    private BigDecimal sessionBudgetYuan = new BigDecimal("1.00");
    @Value("${entertainment.guess-stock.ai.review-rounds:24}")
    private int reviewRounds = 24;
    @Value("${entertainment.guess-stock.ai.min-model-gap-rounds:8}")
    private int minModelGapRounds = 8;
    @Value("${entertainment.guess-stock.ai.max-output-tokens:384}")
    private int maxOutputTokens = 384;
    @Value("${entertainment.guess-stock.ai.max-calls-per-ai:12}")
    private int maxCallsPerAi = 12;
    @Value("${entertainment.guess-stock.ai.unknown-input-yuan-per-million:9}")
    private BigDecimal unknownInputRate = new BigDecimal("9");
    @Value("${entertainment.guess-stock.ai.unknown-cached-yuan-per-million:0.30}")
    private BigDecimal unknownCachedRate = new BigDecimal("0.30");
    @Value("${entertainment.guess-stock.ai.unknown-output-yuan-per-million:27}")
    private BigDecimal unknownOutputRate = new BigDecimal("27");

    public int outputLimit() { return Math.max(384, Math.min(1024, maxOutputTokens)); }
    public int reviewInterval() { return Math.max(4, reviewRounds); }
    public int modelGap() { return Math.max(1, minModelGapRounds); }
    public int callLimit(int rounds, int aiCount) {
        return Math.max(1, aiCount) * Math.max(1, Math.min(maxCallsPerAi,
                (int) Math.ceil(Math.max(1, rounds) / (double) reviewInterval()) + 9));
    }
}
