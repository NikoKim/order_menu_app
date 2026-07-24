package com.order.menu.module.message.service.presence;

import java.util.Set;

public class NoopChatPresenceService implements ChatPresenceService {

    @Override
    public void heartbeat(Long userId) {
    }

    @Override
    public boolean isOnline(Long userId) {
        return false;
    }

    @Override
    public void setTyping(Long userId, Long conversationId, boolean active) {
    }

    @Override
    public Set<Long> typingUsers(Long userId, Long conversationId) {
        return Set.of();
    }
}
