package com.order.menu.module.moment.entity;
import com.baomidou.mybatisplus.annotation.*; import java.time.LocalDateTime; import lombok.Data;
@Data @TableName("moment_reactions") public class MomentReaction { @TableId(type=IdType.AUTO) private Long id; private Long momentId; private Long userId; private String emoji; private LocalDateTime createdAt; }
