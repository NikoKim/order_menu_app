package com.order.menu.module.entertainment.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.order.menu.module.entertainment.core.entity.GameRoom;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface GameRoomMapper extends BaseMapper<GameRoom> {
    @Select("SELECT * FROM game_rooms WHERE id = #{id} FOR UPDATE")
    GameRoom selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM game_rooms WHERE invite_code = #{code} FOR UPDATE")
    GameRoom selectByInviteCodeForUpdate(@Param("code") String code);

    @Select("""
        SELECT r.* FROM game_rooms r
        JOIN game_room_members m ON m.room_id = r.id
        WHERE r.game_id = #{gameId}
          AND r.status IN ('LOBBY', 'PLAYING')
          AND m.player_identity_key = #{identityKey}
          AND m.status = 'ACTIVE'
        ORDER BY r.id DESC
        LIMIT 1
        """)
    GameRoom selectActiveForPlayer(@Param("gameId") Long gameId,
                                   @Param("identityKey") String identityKey);
}
