package com.order.menu.module.entertainment.mode.guessstock.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("guess_stock_loan_ledgers")
public class LoanLedger {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Long participantId;
    private Long facilityId;
    private Long contractId;
    private Integer roundNo;
    private String entryType;
    private BigDecimal principalDelta;
    private BigDecimal interestDelta;
    private BigDecimal cashDelta;
    private BigDecimal beforePrincipal;
    private BigDecimal afterPrincipal;
    private BigDecimal beforeInterest;
    private BigDecimal afterInterest;
    private String requestId;
    private String reason;
    private LocalDateTime createdAt;
}
