package com.order.menu.module.moment.entity;
import com.baomidou.mybatisplus.annotation.*; import java.time.LocalDateTime; import lombok.Data;
@Data @TableName("moment_comment_likes") public class MomentCommentLike { @TableId(type=IdType.AUTO) private Long id; private Long commentId; private Long userId; private LocalDateTime createdAt; }
