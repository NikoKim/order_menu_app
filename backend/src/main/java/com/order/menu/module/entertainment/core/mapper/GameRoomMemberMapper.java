package com.order.menu.module.entertainment.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.order.menu.module.entertainment.core.entity.GameRoomMember;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface GameRoomMemberMapper extends BaseMapper<GameRoomMember> {
    @Select("SELECT * FROM game_room_members WHERE room_id = #{roomId} AND player_identity_key = #{identityKey} FOR UPDATE")
    GameRoomMember selectForUpdate(@Param("roomId") Long roomId, @Param("identityKey") String identityKey);
}
