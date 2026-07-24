package com.order.menu.common.redis;

import com.order.menu.config.OrderMenuRedisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisKeyFactory {

    private final OrderMenuRedisProperties properties;

    private String prefix() {
        return "oma:" + properties.getEnvironment();
    }

    public String shopDetail(Long shopId) {
        return prefix() + ":shop:detail:v1:" + shopId;
    }

    public String shopMenu(Long shopId) {
        return prefix() + ":shop:menu:v1:" + shopId;
    }

    public String momentBase(Long momentId) {
        return prefix() + ":moment:base:v1:" + momentId;
    }

    public String momentFeedVersion() {
        return prefix() + ":moment:feed:version";
    }

    public String momentFeedViewerVersion(Long userId) {
        return prefix() + ":moment:feed:viewer-version:" + userId;
    }

    public String momentFeed(Long userId, String type, long version, long viewerVersion, long page, long size) {
        return prefix() + ":moment:feed:v2:" + userId + ":" + type + ":" + version + ":" + viewerVersion + ":" + page + ":" + size;
    }

    public String momentTopicVersion(Long topicId) {
        return prefix() + ":moment:topic:version:" + topicId;
    }

    public String momentTopicIds(Long viewerId, Long topicId, long version, long page, long size) {
        return prefix() + ":moment:topic-ids:v1:" + viewerId + ":" + topicId + ":" + version + ":" + page + ":" + size;
    }

    public String momentUserVersion(Long userId) {
        return prefix() + ":moment:user:version:" + userId;
    }

    public String momentUserIds(Long viewerId, Long userId, long version, long page, long size) {
        return prefix() + ":moment:user-ids:v1:" + viewerId + ":" + userId + ":" + version + ":" + page + ":" + size;
    }

    public String momentUnread(Long userId) {
        return prefix() + ":moment:unread:v1:" + userId;
    }

    public String momentHotTopics() {
        return prefix() + ":moment:topic:hot:v1";
    }

    public String momentHotIds() {
        return prefix() + ":moment:hot-ids:v1";
    }

    public String momentCommentsFirst(Long momentId, long size) {
        return prefix() + ":moment:comments:first:v1:" + momentId + ":" + size;
    }

    public String momentHotComment(Long momentId) {
        return prefix() + ":moment:hot-comment:v1:" + momentId;
    }

    public String momentStats(Long momentId) {
        return prefix() + ":moment:stats:v1:" + momentId;
    }

    public String momentViewerState(Long viewerId, Long momentId) {
        return prefix() + ":moment:viewer-state:v1:" + viewerId + ":" + momentId;
    }

    public String chatRecentIds(Long conversationId) {
        return prefix() + ":chat:recent-ids:v1:" + conversationId;
    }

    public String chatMessage(Long messageId) {
        return prefix() + ":chat:message:v1:" + messageId;
    }

    public String notificationUnread(Long userId) {
        return prefix() + ":notification:unread:v1:" + userId;
    }

    public String chatUnread(Long userId, Long conversationId) {
        return prefix() + ":chat:unread:v1:" + userId + ":" + conversationId;
    }

    public String chatUnreadTotal(Long userId) {
        return prefix() + ":chat:unread-total:v1:" + userId;
    }

    public String orderIdempotency(Long userId, String clientRequestId) {
        return prefix() + ":order:idempotency:v1:" + userId + ":" + clientRequestId;
    }

    public String websocketChannel() {
        return prefix() + ":ws:events";
    }

    public String userOnline(Long userId) {
        return prefix() + ":presence:online:v1:" + userId;
    }

    public String conversationTyping(Long conversationId) {
        return prefix() + ":presence:typing:v1:" + conversationId;
    }

    public String rateLimit(String action, String subject) {
        return prefix() + ":rate:" + action + ":v1:" + subject;
    }
}
