package com.order.menu.module.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_blocks")
public class ChatBlock {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long blockerUserId;
    private Long blockedUserId;
    private LocalDateTime createdAt;
}
