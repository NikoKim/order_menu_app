package com.order.menu.module.message.service.cache;

import java.util.Optional;

public class NoopUnreadCountCache implements UnreadCountCache {

    @Override public Optional<Long> getNotificationUnread(Long userId) { return Optional.empty(); }
    @Override public void putNotificationUnread(Long userId, long count) {}
    @Override public void evictNotificationUnread(Long userId) {}
    @Override public Optional<Long> getChatUnread(Long userId, Long conversationId) { return Optional.empty(); }
    @Override public void putChatUnread(Long userId, Long conversationId, long count) {}
    @Override public void evictChatUnread(Long userId, Long conversationId) {}
    @Override public Optional<Long> getChatUnreadTotal(Long userId) { return Optional.empty(); }
    @Override public void putChatUnreadTotal(Long userId, long count) {}
    @Override public void evictChatUnreadTotal(Long userId) {}
}
