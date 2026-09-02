package com.order.menu.module.entertainment.mode.guessstock.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("guess_stock_credit_snapshots")
public class CreditSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Long participantId;
    private Integer roundNo;
    private String stage;
    private String productCode;
    private BigDecimal cashValue;
    private BigDecimal stockValue;
    private BigDecimal netAsset;
    private BigDecimal principal;
    private BigDecimal accruedInterest;
    private BigDecimal collateralValue;
    private BigDecimal exposurePercent;
    private BigDecimal ltvPercent;
    private String riskState;
    private Long forcedLiquidationShares;
    private LocalDateTime createdAt;
}
