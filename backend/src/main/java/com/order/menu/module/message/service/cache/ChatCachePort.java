package com.order.menu.module.message.service.cache;

import com.order.menu.module.message.dto.ChatMessageCacheDto;

import java.util.List;
import java.util.Optional;

public interface ChatCachePort {

    Optional<List<ChatMessageCacheDto>> getRecentMessages(Long conversationId, int limit);

    void cacheRecentMessages(Long conversationId, List<ChatMessageCacheDto> messages);

    void appendMessage(ChatMessageCacheDto message);

    void evictMessage(Long conversationId, Long messageId);
}
