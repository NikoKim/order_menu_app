package com.order.menu.module.message.service.cache;

import java.util.Optional;

public interface UnreadCountCache {

    Optional<Long> getNotificationUnread(Long userId);

    void putNotificationUnread(Long userId, long count);

    void evictNotificationUnread(Long userId);

    Optional<Long> getChatUnread(Long userId, Long conversationId);

    void putChatUnread(Long userId, Long conversationId, long count);

    void evictChatUnread(Long userId, Long conversationId);

    Optional<Long> getChatUnreadTotal(Long userId);

    void putChatUnreadTotal(Long userId, long count);

    void evictChatUnreadTotal(Long userId);
}
