package com.order.menu.module.moment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.order.menu.module.moment.entity.Moment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface MomentMapper extends BaseMapper<Moment> {
  @Select("SELECT * FROM moments WHERE id = #{id} FOR UPDATE")
  Moment selectByIdForUpdate(@Param("id") Long id);

  @Update("UPDATE moments SET share_count = share_count + 1, app_share_count = app_share_count + 1 WHERE id = #{id}")
  int incrementAppShareCount(@Param("id") Long id);

  @Update("UPDATE moments SET share_count = share_count + 1, external_share_count = external_share_count + 1 WHERE id = #{id}")
  int incrementExternalShareCount(@Param("id") Long id);
}
