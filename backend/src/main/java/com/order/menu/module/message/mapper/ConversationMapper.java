package com.order.menu.module.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.order.menu.module.message.entity.Conversation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ConversationMapper extends BaseMapper<Conversation> {
    @Select("SELECT * FROM conversations WHERE id = #{id} FOR UPDATE")
    Conversation selectByIdForUpdate(@Param("id") Long id);
}
