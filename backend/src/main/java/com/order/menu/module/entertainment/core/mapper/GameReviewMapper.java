package com.order.menu.module.entertainment.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.order.menu.module.entertainment.core.entity.GameReview;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface GameReviewMapper extends BaseMapper<GameReview> {
    @Select("""
        SELECT EXISTS (
          SELECT 1 FROM game_sessions s
          WHERE s.game_id = #{gameId} AND s.player_user_id = #{userId} AND s.started_at IS NOT NULL
        ) OR EXISTS (
          SELECT 1 FROM guess_stock_participants p JOIN game_sessions s ON s.id = p.session_id
          WHERE s.game_id = #{gameId} AND p.player_user_id = #{userId}
            AND p.participant_type = 'PLAYER' AND s.started_at IS NOT NULL
        )
        """)
    boolean hasPlayed(@Param("gameId") Long gameId, @Param("userId") Long userId);

    @Select("SELECT COALESCE(AVG(rating), 0) FROM game_reviews WHERE game_id = #{gameId}")
    BigDecimal averageRating(@Param("gameId") Long gameId);

    @Insert("""
        INSERT INTO game_reviews (game_id, user_id, rating, content)
        VALUES (#{gameId}, #{userId}, #{rating}, #{content})
        ON DUPLICATE KEY UPDATE rating = VALUES(rating), content = VALUES(content),
          updated_at = CURRENT_TIMESTAMP(3)
        """)
    int upsert(@Param("gameId") Long gameId, @Param("userId") Long userId,
               @Param("rating") int rating, @Param("content") String content);
}
