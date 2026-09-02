package com.order.menu.module.entertainment.mode.guessstock.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("guess_stock_orders")
public class GuessStockOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Long participantId;
    private Integer roundNo;
    private String stage;
    private String side;
    private Long quantity;
    private BigDecimal targetExposurePercent;
    private String status;
    private BigDecimal reservedAmount;
    private Integer allIn;
    private String clientRequestId;
    private LocalDateTime submittedAt;
    private LocalDateTime updatedAt;
}
