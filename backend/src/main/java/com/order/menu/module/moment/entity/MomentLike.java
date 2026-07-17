package com.order.menu.module.moment.entity;
import com.baomidou.mybatisplus.annotation.*; import java.time.LocalDateTime; import lombok.Data;
@Data @TableName("moment_likes") public class MomentLike { @TableId(type=IdType.AUTO) private Long id; private Long momentId; private Long userId; private LocalDateTime createdAt; }
