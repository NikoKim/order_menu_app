package com.order.menu.module.entertainment.mode.guessstock.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("guess_stock_loan_contracts")
public class LoanContract {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long facilityId;
    private BigDecimal principalOriginal;
    private BigDecimal principalOutstanding;
    private BigDecimal interestRate;
    private Integer openedRound;
    private Integer maturityRound;
    private Integer originalMaturityRound;
    private Integer extensionCount;
    private Integer totalExtendedRounds;
    private Integer lastExtendedRound;
    private Integer lastInterestRound;
    private BigDecimal accruedInterest;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
