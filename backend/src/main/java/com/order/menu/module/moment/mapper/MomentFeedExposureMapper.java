package com.order.menu.module.moment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.order.menu.module.moment.entity.MomentFeedExposure;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface MomentFeedExposureMapper extends BaseMapper<MomentFeedExposure> {

    @Select("""
            <script>
            SELECT moment_id
            FROM moment_feed_exposures
            WHERE user_id = #{userId}
              AND feed_type = #{feedType}
              AND moment_id IN
              <foreach collection="momentIds" item="momentId" open="(" separator="," close=")">
                #{momentId}
              </foreach>
            </script>
            """)
    List<Long> selectExposedMomentIds(
            @Param("userId") Long userId,
            @Param("feedType") String feedType,
            @Param("momentIds") Collection<Long> momentIds
    );

    @Insert("""
            <script>
            INSERT IGNORE INTO moment_feed_exposures
                (user_id, moment_id, feed_type, exposed_at)
            VALUES
            <foreach collection="momentIds" item="momentId" separator=",">
                (#{userId}, #{momentId}, #{feedType}, #{exposedAt})
            </foreach>
            </script>
            """)
    int insertIgnoreBatch(
            @Param("userId") Long userId,
            @Param("feedType") String feedType,
            @Param("momentIds") Collection<Long> momentIds,
            @Param("exposedAt") LocalDateTime exposedAt
    );
}
