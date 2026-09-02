package com.order.menu.module.entertainment.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.order.menu.module.entertainment.core.entity.GameSession;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface GameSessionMapper extends BaseMapper<GameSession> {
    @Select("SELECT * FROM game_sessions WHERE id = #{id} FOR UPDATE")
    GameSession selectByIdForUpdate(@Param("id") Long id);

    @Select("""
        SELECT id FROM game_sessions
        WHERE status = 'ACTIVE' AND phase_ends_at IS NOT NULL AND phase_ends_at <= #{now}
        ORDER BY phase_ends_at LIMIT #{limit}
        """)
    List<Long> selectDueIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Select("""
        SELECT id FROM game_sessions
        WHERE status = 'ACTIVE' AND activity_lease_until IS NOT NULL AND activity_lease_until <= #{now}
        ORDER BY activity_lease_until LIMIT #{limit}
        """)
    List<Long> selectExpiredLeaseIds(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
