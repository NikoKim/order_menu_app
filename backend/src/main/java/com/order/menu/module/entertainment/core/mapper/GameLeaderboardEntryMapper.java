package com.order.menu.module.entertainment.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.order.menu.module.entertainment.core.entity.GameLeaderboardEntry;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface GameLeaderboardEntryMapper extends BaseMapper<GameLeaderboardEntry> {
    @Select("""
        SELECT COUNT(*) + 1 FROM game_leaderboard_entries
        WHERE game_version_id = #{versionId}
          AND (score > #{score} OR (score = #{score} AND final_asset > #{finalAsset}))
        """)
    long rankFor(@Param("versionId") Long versionId, @Param("score") BigDecimal score,
                 @Param("finalAsset") BigDecimal finalAsset);
}
