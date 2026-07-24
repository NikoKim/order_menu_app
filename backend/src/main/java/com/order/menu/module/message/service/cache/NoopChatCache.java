package com.order.menu.module.message.service.cache;

import com.order.menu.module.message.dto.ChatMessageCacheDto;

import java.util.List;
import java.util.Optional;

public class NoopChatCache implements ChatCachePort {

    @Override
    public Optional<List<ChatMessageCacheDto>> getRecentMessages(Long conversationId, int limit) {
        return Optional.empty();
    }

    @Override
    public void cacheRecentMessages(Long conversationId, List<ChatMessageCacheDto> messages) {
    }

    @Override
    public void appendMessage(ChatMessageCacheDto message) {
    }

    @Override
    public void evictMessage(Long conversationId, Long messageId) {
    }
}
