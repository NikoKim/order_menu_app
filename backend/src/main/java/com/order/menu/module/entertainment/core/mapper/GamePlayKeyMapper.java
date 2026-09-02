package com.order.menu.module.entertainment.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.order.menu.module.entertainment.core.entity.GamePlayKey;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface GamePlayKeyMapper extends BaseMapper<GamePlayKey> {
    @Select("SELECT * FROM game_play_keys WHERE game_id=#{gameId} AND key_hash=#{hash} FOR UPDATE")
    GamePlayKey selectForUse(@Param("gameId") Long gameId, @Param("hash") String hash);
}
