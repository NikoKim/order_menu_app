package com.order.menu.module.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_messages")
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private Long senderUserId;
    private String type;
    private String content;
    private Long dishId;
    private Long shopId;
    private String dishName;
    private String dishImageUrl;
    private BigDecimal dishPrice;
    private String dishDescription;
    private Integer dishSalesCount;
    private LocalDateTime createdAt;
}
