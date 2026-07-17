package com.order.menu.module.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.order.menu.module.message.entity.UserNotification;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserNotificationMapper extends BaseMapper<UserNotification> {
    @Select("""
            SELECT * FROM user_notifications
            WHERE user_id = #{userId} AND dedupe_key = #{dedupeKey}
            FOR UPDATE
            """)
    UserNotification selectByUserAndDedupeKeyForUpdate(@Param("userId") Long userId,
                                                        @Param("dedupeKey") String dedupeKey);
}
