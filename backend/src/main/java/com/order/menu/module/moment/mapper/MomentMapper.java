package com.order.menu.module.moment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.order.menu.module.moment.entity.Moment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface MomentMapper extends BaseMapper<Moment> {
  @Select("SELECT * FROM moments WHERE id = #{id} FOR UPDATE")
  Moment selectByIdForUpdate(@Param("id") Long id);

  @Update("UPDATE moments SET share_count = share_count + 1, app_share_count = app_share_count + 1 WHERE id = #{id}")
  int incrementAppShareCount(@Param("id") Long id);

  @Update("UPDATE moments SET share_count = share_count + 1, external_share_count = external_share_count + 1 WHERE id = #{id}")
  int incrementExternalShareCount(@Param("id") Long id);

  @Select("""
      SELECT m.id
      FROM moments m
      LEFT JOIN (SELECT moment_id, COUNT(*) AS cnt FROM moment_likes GROUP BY moment_id) l ON l.moment_id = m.id
      LEFT JOIN (SELECT moment_id, COUNT(*) AS cnt FROM moment_comments GROUP BY moment_id) c ON c.moment_id = m.id
      LEFT JOIN (SELECT moment_id, COUNT(*) AS cnt FROM moment_reactions GROUP BY moment_id) r ON r.moment_id = m.id
      WHERE m.status = 'PUBLISHED'
      ORDER BY (COALESCE(l.cnt, 0) + COALESCE(c.cnt, 0) + COALESCE(r.cnt, 0) + COALESCE(m.share_count, 0)) DESC,
               m.published_at DESC, m.id DESC
      LIMIT #{limit}
      """)
  List<Long> selectHotCandidateIds(@Param("limit") int limit);
}
