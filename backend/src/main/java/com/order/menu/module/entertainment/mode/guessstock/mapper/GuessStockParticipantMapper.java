package com.order.menu.module.entertainment.mode.guessstock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockParticipant;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface GuessStockParticipantMapper extends BaseMapper<GuessStockParticipant> {
    @Select("SELECT * FROM guess_stock_participants WHERE id = #{id} FOR UPDATE")
    GuessStockParticipant selectByIdForUpdate(@Param("id") Long id);
}
