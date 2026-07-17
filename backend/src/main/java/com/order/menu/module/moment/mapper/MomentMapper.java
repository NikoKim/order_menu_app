package com.order.menu.module.moment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.order.menu.module.moment.entity.Moment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface MomentMapper extends BaseMapper<Moment> {
  @Select("SELECT * FROM moments WHERE id = #{id} FOR UPDATE")
  Moment selectByIdForUpdate(@Param("id") Long id);
}
