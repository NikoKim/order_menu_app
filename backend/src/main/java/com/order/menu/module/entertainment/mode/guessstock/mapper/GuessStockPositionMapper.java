package com.order.menu.module.entertainment.mode.guessstock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockPosition;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface GuessStockPositionMapper extends BaseMapper<GuessStockPosition> {
    @Select("SELECT * FROM guess_stock_positions WHERE participant_id = #{participantId}")
    GuessStockPosition selectByParticipant(@Param("participantId") Long participantId);

    @Select("SELECT * FROM guess_stock_positions WHERE participant_id = #{participantId} FOR UPDATE")
    GuessStockPosition selectByParticipantForUpdate(@Param("participantId") Long participantId);
}
