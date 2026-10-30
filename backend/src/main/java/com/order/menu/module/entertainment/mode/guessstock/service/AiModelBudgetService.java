package com.order.menu.module.entertainment.mode.guessstock.service;

import com.order.menu.module.entertainment.mode.guessstock.agent.AiModelCostPolicy;
import com.order.menu.module.entertainment.mode.guessstock.agent.AiModelSettings;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiModelBudgetService {
    private final JdbcTemplate jdbcTemplate;
    private final AiModelSettings settings;

    // Reserve before HTTP using an atomic database update; concurrent AIs cannot spend the same budget.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reservation reserve(long sessionId, int rounds, int aiCount, AiModelCostPolicy.Rates rates,
                               int inputTokenUpperBound) {
        BigDecimal amount = rates.cost(inputTokenUpperBound, 0, settings.outputLimit());
        // An upsert takes the exclusive row lock directly; INSERT IGNORE can deadlock on lock upgrades.
        jdbcTemplate.update("""
                INSERT INTO guess_stock_ai_budgets (session_id) VALUES (?)
                ON DUPLICATE KEY UPDATE session_id = session_id
                """, sessionId);
        int updated = jdbcTemplate.update("""
                UPDATE guess_stock_ai_budgets
                SET reserved_yuan = reserved_yuan + ?, request_count = request_count + 1
                WHERE session_id = ? AND spent_yuan + reserved_yuan + ? <= ? AND request_count < ?
                """, amount, sessionId, amount, settings.getSessionBudgetYuan().max(BigDecimal.ZERO),
                settings.callLimit(rounds, aiCount));
        if (updated != 1) throw new ModelAiDecisionService.ModelDecisionException("本局模型费用或调用预算已达上限，继续执行本地策略");
        return new Reservation(sessionId, amount, rates);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BigDecimal settle(Reservation reservation, ModelAiDecisionService.InvocationTelemetry telemetry) {
        boolean knownUsage = telemetry != null && telemetry.promptTokens() > 0;
        boolean rejected = telemetry != null && telemetry.httpStatus() >= 400 && telemetry.httpStatus() < 500;
        BigDecimal cost = knownUsage ? reservation.rates().cost(telemetry.promptTokens(),
                telemetry.promptCacheHitTokens(), telemetry.completionTokens())
                : rejected ? BigDecimal.ZERO : reservation.amount();
        jdbcTemplate.update("""
                UPDATE guess_stock_ai_budgets SET reserved_yuan = GREATEST(0, reserved_yuan - ?),
                  spent_yuan = spent_yuan + ?, prompt_tokens = prompt_tokens + ?,
                  cache_hit_tokens = cache_hit_tokens + ?, completion_tokens = completion_tokens + ?
                WHERE session_id = ?
                """, reservation.amount(), cost, telemetry == null ? 0 : telemetry.promptTokens(),
                telemetry == null ? 0 : telemetry.promptCacheHitTokens(),
                telemetry == null ? 0 : telemetry.completionTokens(), reservation.sessionId());
        return cost;
    }

    public record Reservation(long sessionId, BigDecimal amount, AiModelCostPolicy.Rates rates) {}

    public Usage usage(long sessionId) {
        List<Usage> rows = jdbcTemplate.query("""
                SELECT spent_yuan, reserved_yuan, request_count, prompt_tokens, cache_hit_tokens, completion_tokens
                FROM guess_stock_ai_budgets WHERE session_id = ?
                """, (rs, row) -> new Usage(rs.getBigDecimal("spent_yuan"), rs.getBigDecimal("reserved_yuan"),
                settings.getSessionBudgetYuan(), rs.getInt("request_count"), rs.getLong("prompt_tokens"),
                rs.getLong("cache_hit_tokens"), rs.getLong("completion_tokens")), sessionId);
        return rows.isEmpty() ? new Usage(BigDecimal.ZERO, BigDecimal.ZERO, settings.getSessionBudgetYuan(), 0, 0, 0, 0)
                : rows.get(0);
    }

    public record Usage(BigDecimal estimatedCostYuan, BigDecimal reservedYuan, BigDecimal budgetYuan,
                        int requestCount, long promptTokens, long promptCacheHitTokens, long completionTokens) {}
}
