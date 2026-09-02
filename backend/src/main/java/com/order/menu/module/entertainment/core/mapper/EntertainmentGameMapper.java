package com.order.menu.module.entertainment.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.order.menu.module.entertainment.core.entity.EntertainmentGame;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface EntertainmentGameMapper extends BaseMapper<EntertainmentGame> {
    @Select("SELECT * FROM games WHERE id = #{id} FOR UPDATE")
    EntertainmentGame selectByIdForUpdate(@Param("id") Long id);
}
