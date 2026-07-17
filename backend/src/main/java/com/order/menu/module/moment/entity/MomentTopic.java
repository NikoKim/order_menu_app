package com.order.menu.module.moment.entity;
import com.baomidou.mybatisplus.annotation.*; import java.time.LocalDateTime; import lombok.Data;
@Data @TableName("moment_topics") public class MomentTopic { @TableId(type=IdType.AUTO) private Long id; private String name; private String normalizedName; private Long createdByUserId; private Long usageCount; private LocalDateTime createdAt; }
