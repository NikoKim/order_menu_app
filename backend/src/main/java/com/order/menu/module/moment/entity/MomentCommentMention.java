package com.order.menu.module.moment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("moment_comment_mentions")
public class MomentCommentMention {
  private Long commentId;
  private Long userId;
  private LocalDateTime createdAt;
}
