package com.order.menu.module.entertainment.mode.guessstock.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("guess_stock_ai_theses")
public class AiInvestmentThesis {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Long participantId;
    private String thesisCode;
    private Integer version;
    private String status;
    private Integer createdRound;
    private String createdPhase;
    private BigDecimal createdPrice;
    private Integer lastValidatedRound;
    private Integer renewedRound;
    private String marketRegime;
    private String direction;
    private String timeHorizon;
    private BigDecimal targetPositionPct;
    private BigDecimal confidence;
    private Integer reviewAfterRounds;
    private Integer expireAfterRounds;
    private String reasonCodesJson;
    private String invalidConditionsJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
