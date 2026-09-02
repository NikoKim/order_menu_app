package com.order.menu.module.entertainment.mode.guessstock.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("guess_stock_positions")
public class GuessStockPosition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Long participantId;
    private Long quantity;
    private Long reservedQuantity;
    private BigDecimal averageCost;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
