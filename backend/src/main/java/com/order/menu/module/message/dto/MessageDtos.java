package com.order.menu.module.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class MessageDtos {
    private MessageDtos() {
    }

    public record SendMessageRequest(
            @NotBlank(message = "消息内容不能为空")
            @Size(max = 2000, message = "消息内容不能超过2000个字符")
            String content,
            Long quotedMessageId) {
        public SendMessageRequest(String content) {
            this(content, null);
        }
    }

    public record FamilyGroupProfileRequest(
            @Size(max = 100, message = "群聊名称不能超过100个字符") String name,
            @Size(max = 255, message = "群聊封面地址不能超过255个字符") String coverImageUrl,
            @Size(max = 1000, message = "群公告不能超过1000个字符") String announcement,
            Boolean joinApprovalRequired) {
        public FamilyGroupProfileRequest(String name, String coverImageUrl, String announcement) {
            this(name, coverImageUrl, announcement, null);
        }
    }

    public record SetMuteRequest(Boolean muted) {
    }

    public record ShareDishRequest(Long conversationId, Long dishId) {
    }

    public record ShareOrderRequest(Long conversationId, Long orderId) {
    }

    public record ChatBlockResponse(Long userId, Boolean blockedByMe, Boolean blockedMe) {
    }

    public record OrderShareItemResponse(String dishName, String dishImageUrl, BigDecimal unitPrice,
                                         Integer quantity, BigDecimal subtotalAmount, String selectedOptions) {
    }

    public record QuotedMessageSnapshot(
            Long id, String type, String content, Long dishId, Long shopId, String shopName,
            String dishName, String dishImageUrl, BigDecimal dishPrice, String dishDescription,
            Integer dishSalesCount, Long orderId, String orderNo, LocalDateTime orderCreatedAt,
            String orderStatus, String orderShopName, List<OrderShareItemResponse> orderItems,
            BigDecimal orderTotalAmount, String orderRemark) {
    }

    public record CustomerSearchResponse(Long id, String nickname, String avatarUrl) {
    }

    public record ConversationResponse(
            Long id,
            String type,
            String name,
            String coverImageUrl,
            String status,
            LocalDateTime lastMessageAt,
            Long directUserId,
            String directUserNickname,
            String directUserAvatarUrl,
            String lastMessageContent,
            Long unreadCount,
            Boolean muted) {
    }

    public record ChatMessageResponse(
            Long id,
            Long conversationId,
            Long senderUserId,
            String senderNickname,
            String senderAvatarUrl,
            String type,
            String content,
            Long dishId,
            Long shopId,
            String shopName,
            String dishName,
            String dishImageUrl,
            BigDecimal dishPrice,
            String dishDescription,
            Integer dishSalesCount,
            Long orderId,
            String orderNo,
            LocalDateTime orderCreatedAt,
            String orderStatus,
            String orderShopName,
            List<OrderShareItemResponse> orderItems,
            BigDecimal orderTotalAmount,
            String orderRemark,
            Long quotedMessageId,
            String quotedType,
            String quotedContent,
            String quotedSenderName,
            QuotedMessageSnapshot quotedMessage,
            String deliveryStatus,
            Boolean deliveryFailed,
            String failureReason,
            LocalDateTime recalledAt,
            LocalDateTime createdAt) {
    }

    public record FamilyGroupMemberResponse(Long userId, String nickname, String avatarUrl,
                                            String role, String memberStatus, Boolean muted) {
    }

    public record FamilyGroupResponse(Long id, Long shopId, String name, String coverImageUrl,
                                      String announcement, String status, Boolean joinApprovalRequired,
                                      List<FamilyGroupMemberResponse> members) {
    }

    public record ConversationMessagesResponse(
            ConversationResponse conversation,
            List<ChatMessageResponse> messages) {
    }

    public record NotificationResponse(
            Long id,
            Long userId,
            String type,
            String title,
            String content,
            String payload,
            LocalDateTime readAt,
            LocalDateTime createdAt) {
    }

    public record NotificationListResponse(List<NotificationResponse> notifications, Long unreadCount) {
    }
}
