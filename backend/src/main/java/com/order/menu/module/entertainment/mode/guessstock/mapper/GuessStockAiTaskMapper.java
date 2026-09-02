package com.order.menu.module.entertainment.mode.guessstock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockAiTask;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface GuessStockAiTaskMapper extends BaseMapper<GuessStockAiTask> {
    @Select("SELECT * FROM guess_stock_ai_tasks WHERE id = #{id} FOR UPDATE")
    GuessStockAiTask selectByIdForUpdate(@Param("id") Long id);

    @Update("UPDATE guess_stock_ai_tasks SET decision_status = 'PROCESSING', "
            + "execute_at = DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 30 SECOND) "
            + "WHERE id = #{id} AND decision_status = 'PENDING'")
    int claim(@Param("id") Long id);

    @Update("UPDATE guess_stock_ai_tasks SET decision_status = 'PENDING' "
            + "WHERE decision_status = 'PROCESSING' AND execute_at <= #{now}")
    int recoverStale(@Param("now") LocalDateTime now);

    @Select("""
        SELECT t.* FROM guess_stock_ai_tasks t
        JOIN game_sessions s ON s.id = t.session_id
        WHERE t.decision_status = 'PENDING' AND t.execute_at <= #{now} AND s.status = 'ACTIVE'
        ORDER BY t.execute_at LIMIT #{limit}
        """)
    List<GuessStockAiTask> selectDue(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
