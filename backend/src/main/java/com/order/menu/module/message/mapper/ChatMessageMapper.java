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
}
