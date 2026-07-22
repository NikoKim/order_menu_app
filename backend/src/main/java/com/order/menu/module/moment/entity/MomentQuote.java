package com.order.menu.module.moment.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("moment_quotes")
public class MomentQuote {
  @TableId private Long momentId;
  private String quoteType;
  private Long quotedMomentId;
  private Long quotedCommentId;
  private Long authorUserId;
  private String authorNickname;
  private String authorAvatarUrl;
  private String authorRole;
  private String momentTitle;
  private String quotedContent;
  private LocalDateTime quotedAt;
  private LocalDateTime createdAt;
}
