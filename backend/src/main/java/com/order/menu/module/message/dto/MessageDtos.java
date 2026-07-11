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
            @NotBlank(message = "Message content must not be blank")
            @Size(max = 2000, message = "Message content must not exceed 2000 characters")
            String content) {
    }

    public record FamilyGroupProfileRequest(
            @Size(max = 100, message = "Group name must not exceed 100 characters") String name,
            @Size(max = 255, message = "Cover image URL must not exceed 255 characters") String coverImageUrl,
            @Size(max = 1000, message = "Announcement must not exceed 1000 characters") String announcement) {
    }

    public record SetMuteRequest(Boolean muted) {
    }

    public record ShareDishRequest(Long conversationId, Long dishId) {
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
            String dishName,
            String dishImageUrl,
            BigDecimal dishPrice,
            String dishDescription,
            Integer dishSalesCount,
            LocalDateTime createdAt) {
    }

    public record FamilyGroupMemberResponse(Long userId, String nickname, String avatarUrl,
                                            String role, String memberStatus, Boolean muted) {
    }

    public record FamilyGroupResponse(Long id, Long shopId, String name, String coverImageUrl,
                                      String announcement, String status, List<FamilyGroupMemberResponse> members) {
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
