package com.order.menu.module.moment.entity;
import com.baomidou.mybatisplus.annotation.*; import java.time.LocalDateTime; import lombok.Data;
@Data @TableName("moment_user_preferences") public class MomentUserPreference { @TableId(type=IdType.AUTO) private Long id; private Long userId; private Long targetUserId; private Boolean hideTargetMoments; private Boolean hideMyMoments; private LocalDateTime createdAt; private LocalDateTime updatedAt; }
