package com.order.menu.module.entertainment.mode.guessstock.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("guess_stock_financial_reports")
public class GuessStockFinancialReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Integer roundNo;
    private BigDecimal fundamentalScore;
    private BigDecimal expectedScore;
    private BigDecimal surprisePercent;
    private String reportJson;
    private LocalDateTime createdAt;
}
