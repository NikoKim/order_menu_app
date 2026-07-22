package com.order.menu.module.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.order.menu.module.message.entity.ConversationMember;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ConversationMemberMapper extends BaseMapper<ConversationMember> {

    @Update("""
        UPDATE conversation_members
        SET last_read_message_id =
            GREATEST(COALESCE(last_read_message_id, 0), #{lastReadMessageId})
        WHERE conversation_id = #{conversationId}
          AND user_id = #{userId}
        """)
    int updateLastReadMessageId(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId,
            @Param("lastReadMessageId") Long lastReadMessageId
    );
}
