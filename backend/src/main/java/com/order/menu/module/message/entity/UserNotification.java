package com.order.menu.module.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("user_notifications")
public class UserNotification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String type;
    private String title;
    private String content;
    private String payload;
    private String dedupeKey;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
