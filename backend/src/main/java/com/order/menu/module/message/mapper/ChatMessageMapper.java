package com.order.menu.module.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.order.menu.module.message.entity.ChatMessage;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
    @Select("""
            SELECT DISTINCT peer.user_id
            FROM conversation_members self
            JOIN conversations conversation
              ON conversation.id = self.conversation_id
             AND conversation.type = 'DIRECT'
             AND conversation.status = 'ACTIVE'
            JOIN conversation_members peer
              ON peer.conversation_id = conversation.id
             AND peer.user_id <> self.user_id
             AND peer.member_status = 'ACTIVE'
            WHERE self.user_id = #{userId}
              AND self.member_status = 'ACTIVE'
              AND EXISTS (
                SELECT 1 FROM chat_messages mine
                WHERE mine.conversation_id = conversation.id
                  AND mine.sender_user_id = self.user_id
                  AND mine.recalled_at IS NULL
                  AND mine.delivery_status = 'DELIVERED'
              )
              AND EXISTS (
                SELECT 1 FROM chat_messages theirs
                WHERE theirs.conversation_id = conversation.id
                  AND theirs.sender_user_id = peer.user_id
                  AND theirs.recalled_at IS NULL
                  AND theirs.delivery_status = 'DELIVERED'
              )
            """)
    List<Long> selectEstablishedDirectChatUserIds(@Param("userId") Long userId);

    @Select("""
        SELECT m.*
        FROM chat_messages m
        WHERE m.conversation_id = #{conversationId}
          AND NOT EXISTS (
              SELECT 1
              FROM chat_message_deletions d
              WHERE d.message_id = m.id
                AND d.user_id = #{userId}
          )
          AND (m.delivery_status IS NULL OR m.delivery_status <> 'FAILED_BLOCKED'
               OR m.sender_user_id = #{userId})
        ORDER BY m.id DESC
        LIMIT #{limit}
        """)
    List<ChatMessage> selectVisibleLatest(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId,
            @Param("limit") int limit
    );

    @Select("""
        SELECT m.*
        FROM chat_messages m
        WHERE m.conversation_id = #{conversationId}
          AND m.id < #{beforeId}
          AND NOT EXISTS (
              SELECT 1
              FROM chat_message_deletions d
              WHERE d.message_id = m.id
                AND d.user_id = #{userId}
          )
          AND (m.delivery_status IS NULL OR m.delivery_status <> 'FAILED_BLOCKED'
               OR m.sender_user_id = #{userId})
        ORDER BY m.id DESC
        LIMIT #{limit}
        """)
    List<ChatMessage> selectVisibleBefore(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId,
            @Param("beforeId") Long beforeId,
            @Param("limit") int limit
    );

    @Select("""
        SELECT COUNT(*)
        FROM chat_messages m
        WHERE m.conversation_id = #{conversationId}
          AND m.sender_user_id <> #{userId}
          AND (#{lastReadMessageId} IS NULL OR m.id > #{lastReadMessageId})
          AND (m.delivery_status IS NULL OR m.delivery_status <> 'FAILED_BLOCKED')
          AND NOT EXISTS (
              SELECT 1
              FROM chat_message_deletions d
              WHERE d.message_id = m.id
                AND d.user_id = #{userId}
          )
        """)
    long countVisibleUnread(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId,
            @Param("lastReadMessageId") Long lastReadMessageId
    );

    @Select("""
        SELECT COUNT(*)
        FROM chat_messages
        WHERE id = #{messageId}
          AND conversation_id = #{conversationId}
        """)
    int countMessageInConversation(
            @Param("conversationId") Long conversationId,
            @Param("messageId") Long messageId
    );
}
