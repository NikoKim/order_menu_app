package com.order.menu.module.moment.service.cache;

import com.order.menu.config.OrderMenuRedisProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

final class MomentAuxiliaryCacheKeys {

    private final String prefix;

    MomentAuxiliaryCacheKeys(OrderMenuRedisProperties properties) {
        this.prefix = "oma:" + properties.getEnvironment() + ":moment:aux";
    }

    String commentLike(Long viewerId, Long commentId) {
        return prefix + ":comment-like:v1:" + viewerId + ":" + commentId;
    }

    String author(Long userId) {
        return prefix + ":author:v1:" + userId;
    }

    String mentionSearchVersion() {
        return prefix + ":mention-search:version";
    }

    String mentionSearch(String query, long version) {
        return prefix + ":mention-search:v1:" + version + ":" + queryHash(query);
    }

    String topic(Long topicId) {
        return prefix + ":topic:v1:" + topicId;
    }

    String topicSearchVersion() {
        return prefix + ":topic-search:version";
    }

    String topicSearch(String query, long version, long page, long pageSize) {
        return prefix + ":topic-search:v1:" + version + ":" + queryHash(query)
                + ":" + page + ":" + pageSize;
    }

    String missingMoment(Long momentId) {
        return prefix + ":missing-moment:v1:" + momentId;
    }

    String missingComment(Long commentId) {
        return prefix + ":missing-comment:v1:" + commentId;
    }

    private String queryHash(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
