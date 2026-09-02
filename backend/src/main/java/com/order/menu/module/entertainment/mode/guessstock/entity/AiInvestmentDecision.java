package com.order.menu.module.entertainment.mode.guessstock.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("guess_stock_ai_decisions")
public class AiInvestmentDecision {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Long participantId;
    private Long thesisId;
    private Integer roundNo;
    private String phase;
    private String decisionType;
    private BigDecimal previousTargetPositionPct;
    private BigDecimal targetPositionPct;
    private BigDecimal requestedTargetPositionPct;
    private BigDecimal stepTargetPositionPct;
    private BigDecimal resultingPositionPct;
    private BigDecimal currentPositionPct;
    private BigDecimal confidence;
    private String urgency;
    private Integer materialChange;
    private String newEvidenceJson;
    private String triggeredConditionsJson;
    private String reasonCodesJson;
    private String executedDirection;
    private Long executedShares;
    private BigDecimal executedValue;
    private String executionReason;
    private LocalDateTime createdAt;
}
