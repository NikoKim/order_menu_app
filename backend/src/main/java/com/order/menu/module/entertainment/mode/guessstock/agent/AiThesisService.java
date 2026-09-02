package com.order.menu.module.entertainment.mode.guessstock.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.menu.module.entertainment.mode.guessstock.entity.AiInvestmentThesis;
import com.order.menu.module.entertainment.mode.guessstock.mapper.AiInvestmentThesisMapper;
import com.order.menu.module.entertainment.mode.guessstock.service.ModelAiDecisionService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiThesisService {
    private final AiInvestmentThesisMapper mapper;
    private final ObjectMapper objectMapper;

    public AiThesisService(AiInvestmentThesisMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public AiInvestmentThesis findActive(Long sessionId, Long participantId) {
        return mapper.selectOne(new LambdaQueryWrapper<AiInvestmentThesis>()
                .eq(AiInvestmentThesis::getSessionId, sessionId)
                .eq(AiInvestmentThesis::getParticipantId, participantId)
                .eq(AiInvestmentThesis::getStatus, "ACTIVE").orderByDesc(AiInvestmentThesis::getId).last("LIMIT 1"));
    }

    public AiInvestmentThesis apply(Long sessionId, Long participantId, int roundNo, String phase,
                                    BigDecimal price, AiInvestmentThesis current,
                                    ModelAiDecisionService.Decision decision,
                                    AiThesisConditionEvaluator.ValidationResult validation,
                                    AiEvidenceDetector.EvidenceResult evidence) {
        LocalDateTime now = LocalDateTime.now();
        return switch (decision.decisionType()) {
            case "CREATE_THESIS" -> create(sessionId, participantId, roundNo, phase, price, decision, now);
            case "KEEP" -> {
                boolean reviewed = evidence.evidences().stream().anyMatch(item ->
                        "THESIS_REVIEW_DUE".equals(item.type()) || "THESIS_EXPIRED".equals(item.type()));
                boolean renewed = evidence.evidences().stream().anyMatch(item -> "THESIS_EXPIRED".equals(item.type()));
                if (reviewed) current.setLastValidatedRound(roundNo);
                if (renewed) {
                    current.setRenewedRound(roundNo);
                    current.setVersion(current.getVersion() + 1);
                }
                current.setConfidence(decimal(decision.confidence()));
                current.setUpdatedAt(now);
                mapper.updateById(current);
                yield current;
            }
            case "ADJUST" -> {
                update(current, roundNo, decision, now);
                yield current;
            }
            case "REPLACE_THESIS" -> {
                current.setStatus(validation.invalidated() ? "INVALIDATED" : "CLOSED");
                current.setUpdatedAt(now);
                mapper.updateById(current);
                yield create(sessionId, participantId, roundNo, phase, price, decision, now);
            }
            case "CLOSE_THESIS" -> {
                current.setStatus(validation.invalidated() ? "INVALIDATED" : "CLOSED");
                current.setLastValidatedRound(roundNo);
                current.setUpdatedAt(now);
                mapper.updateById(current);
                yield current;
            }
            default -> throw new IllegalArgumentException("不支持的观点决策类型");
        };
    }

    public List<AiInvalidCondition> conditions(AiInvestmentThesis thesis) {
        if (thesis == null || thesis.getInvalidConditionsJson() == null) return List.of();
        try { return objectMapper.readValue(thesis.getInvalidConditionsJson(), new TypeReference<>() {}); }
        catch (JsonProcessingException ignored) { return List.of(); }
    }

    public void invalidate(AiInvestmentThesis thesis, int roundNo) {
        if (thesis == null || !"ACTIVE".equals(thesis.getStatus())) return;
        thesis.setStatus("INVALIDATED");
        thesis.setLastValidatedRound(roundNo);
        thesis.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(thesis);
    }

    private AiInvestmentThesis create(Long sessionId, Long participantId, int roundNo, String phase,
                                      BigDecimal price, ModelAiDecisionService.Decision decision, LocalDateTime now) {
        long sequence = mapper.selectCount(new LambdaQueryWrapper<AiInvestmentThesis>()
                .eq(AiInvestmentThesis::getSessionId, sessionId)
                .eq(AiInvestmentThesis::getParticipantId, participantId)) + 1;
        AiInvestmentThesis thesis = new AiInvestmentThesis();
        thesis.setSessionId(sessionId);
        thesis.setParticipantId(participantId);
        thesis.setThesisCode("AI%02d-T%d".formatted(participantId, sequence));
        thesis.setVersion(1);
        thesis.setStatus("ACTIVE");
        thesis.setCreatedRound(roundNo);
        thesis.setCreatedPhase(phase);
        thesis.setCreatedPrice(price);
        thesis.setCreatedAt(now);
        thesis.setRenewedRound(roundNo);
        update(thesis, roundNo, decision, now);
        mapper.insert(thesis);
        return thesis;
    }

    private void update(AiInvestmentThesis thesis, int roundNo, ModelAiDecisionService.Decision decision,
                        LocalDateTime now) {
        if (thesis.getId() != null) thesis.setVersion(thesis.getVersion() + 1);
        if (thesis.getId() != null) thesis.setRenewedRound(roundNo);
        thesis.setLastValidatedRound(roundNo);
        thesis.setMarketRegime(decision.marketRegime());
        thesis.setDirection(decision.direction());
        thesis.setTimeHorizon(decision.timeHorizon());
        thesis.setTargetPositionPct(decimal(decision.targetPositionPct()));
        thesis.setConfidence(decimal(decision.confidence()));
        thesis.setReviewAfterRounds(decision.reviewAfterRounds());
        thesis.setExpireAfterRounds(decision.expireAfterRounds());
        thesis.setReasonCodesJson(write(decision.reasonCodes()));
        thesis.setInvalidConditionsJson(write(decision.invalidConditions()));
        thesis.setUpdatedAt(now);
        if (thesis.getId() != null) mapper.updateById(thesis);
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("AI 观点序列化失败", exception); }
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(4, java.math.RoundingMode.HALF_UP);
    }
}
