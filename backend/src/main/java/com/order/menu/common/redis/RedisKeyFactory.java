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
}
