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
    private String deliveryStatus;
    private String deliveryFailureReason;
    private Long dishId;
    private Long shopId;
    private String shopName;
    private String dishName;
    private String dishImageUrl;
    private BigDecimal dishPrice;
    private String dishDescription;
    private Integer dishSalesCount;
    private Long orderId;
    private String orderNo;
    private LocalDateTime orderCreatedAt;
    private String orderStatus;
    private String orderShopName;
    private String orderItemsJson;
    private BigDecimal orderTotalAmount;
    private String orderRemark;
    private String imageUrl;
    private String imageStoragePath;
    private LocalDateTime imageExpiresAt;
    private Long sharedShopId;
    private String sharedShopName;
    private String sharedShopLogoUrl;
    private String sharedShopDescription;
    private Long sharedMomentId;
    private Long sharedMomentCommentId;
    private String sharedMomentTitle;
    private String sharedMomentContent;
    private String sharedMomentPreview;
    private String sharedCommentPreview;
    private String sharedMomentAuthorName;
    private String sharedMomentAuthorAvatar;
    private String sharedMomentCoverUrl;
    private String sharedCardIcon;
    private Long quotedMessageId;
    private String quotedType;
    private String quotedContent;
    private String quotedSenderName;
    private LocalDateTime recalledAt;
    private LocalDateTime createdAt;
}
