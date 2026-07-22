package com.order.menu.module.moment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("moment_mentions")
public class MomentMention {
  private Long momentId;
  private Long userId;
  private LocalDateTime createdAt;
}
