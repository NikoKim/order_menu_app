package com.order.menu.module.entertainment.mode.guessstock.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("guess_stock_event_impact_slices")
public class GuessStockEventImpactSlice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Integer sourceRoundNo;
    private String sourceStage;
    private Integer eventConfigIndex;
    private Integer targetRoundNo;
    private String targetStage;
    private String direction;
    private String eventType;
    private BigDecimal totalImpactPercent;
    private BigDecimal sliceWeight;
    private BigDecimal sliceImpactPercent;
    private BigDecimal saturationFactor;
    private BigDecimal contrarianFactor;
    private LocalDateTime appliedAt;
    private LocalDateTime createdAt;
}
