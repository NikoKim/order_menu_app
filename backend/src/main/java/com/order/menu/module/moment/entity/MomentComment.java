package com.order.menu.module.moment.entity;
import com.baomidou.mybatisplus.annotation.*; import java.time.LocalDateTime; import lombok.Data;
@Data @TableName("moment_comments") public class MomentComment { @TableId(type=IdType.AUTO) private Long id; private Long momentId; private Long authorUserId; private Long parentCommentId; private Long replyToCommentId; private String content; private String imageUrls; private LocalDateTime createdAt; private LocalDateTime updatedAt; }
