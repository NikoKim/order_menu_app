package com.order.menu.module.moment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record MomentBaseCacheDto(
        int schemaVersion,
        Long id,
        Long authorUserId,
        String authorNickname,
        String authorAvatar,
        String authorRole,
        String title,
        String content,
        String visibility,
        String status,
        List<String> imageUrls,
        long likeCount,
        long commentCount,
        long rootCommentCount,
        long shareCount,
        long appShareCount,
        long externalShareCount,
        List<ReactionSummary> reactionSummaries,
        Long shopId,
        String shopName,
        String shopImageUrl,
        Long dishId,
        String dishName,
        String dishImageUrl,
        BigDecimal dishPrice,
        String dishDescription,
        Integer dishSalesCount,
        List<TopicSummary> topics,
        Long quotedMomentId,
        Long quotedCommentId,
        String quoteType,
        Long quoteAuthorUserId,
        String quoteAuthorNickname,
        String quoteAuthorAvatar,
        String quoteAuthorRole,
        String quoteMomentTitle,
        String quoteContent,
        LocalDateTime quoteCreatedAt,
        Long hotCommentId,
        List<Long> mentionUserIds,
        LocalDateTime editedAt,
        LocalDateTime publishedAt,
        LocalDateTime createdAt
) {
    public MomentBaseCacheDto {
        schemaVersion = 1;
    }

    public record ReactionSummary(String emoji, long count) {}

    public record TopicSummary(Long id, String name, Long usageCount, Long userCount, Long momentCount) {}
}
