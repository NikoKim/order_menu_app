package com.order.menu.module.moment.entity; import com.baomidou.mybatisplus.annotation.*; import java.time.LocalDateTime; import lombok.Data;
@Data @TableName("moment_feed_exposures") public class MomentFeedExposure { @TableId(type=IdType.AUTO) private Long id; private Long userId; private Long momentId; private String feedType; private LocalDateTime exposedAt; }
