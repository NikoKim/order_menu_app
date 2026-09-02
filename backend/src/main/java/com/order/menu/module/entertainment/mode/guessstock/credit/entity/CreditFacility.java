package com.order.menu.module.entertainment.mode.guessstock.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("guess_stock_credit_facilities")
public class CreditFacility {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Long participantId;
    private String productCode;
    private String productName;
    private String riskLevel;
    private String productSnapshotJson;
    private String status;
    private BigDecimal principalOutstanding;
    private BigDecimal accruedInterest;
    private Integer lastBorrowRound;
    private Integer lastRepayRound;
    private Integer createdRound;
    private Integer closedRound;
    @Version
    private Long rowVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
