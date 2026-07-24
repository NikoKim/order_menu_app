package com.order.menu.module.message.dto;

import com.order.menu.module.message.entity.ChatMessage;

/**
 * Versioned cache envelope for a complete message row. Keeping the complete
 * entity is important because share cards, image expiry and quoted snapshots
 * all depend on fields beyond the common text-message columns.
 */
public record ChatMessageCacheDto(int schemaVersion, ChatMessage message) {

    private static final int CURRENT_SCHEMA_VERSION = 2;

    public ChatMessageCacheDto {
        if (schemaVersion != CURRENT_SCHEMA_VERSION || message == null) {
            throw new IllegalArgumentException("Unsupported chat message cache schema");
        }
    }

    public static ChatMessageCacheDto from(ChatMessage message) {
        return new ChatMessageCacheDto(CURRENT_SCHEMA_VERSION, message);
    }

    public Long id() {
        return message.getId();
    }

    public Long conversationId() {
        return message.getConversationId();
    }

    public ChatMessage toEntity() {
        return message;
    }
}
