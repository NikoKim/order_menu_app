package com.order.menu.module.message.service.presence;

import java.util.Set;

public interface ChatPresenceService {

    void heartbeat(Long userId);

    boolean isOnline(Long userId);

    void setTyping(Long userId, Long conversationId, boolean active);

    Set<Long> typingUsers(Long userId, Long conversationId);
}
