package com.order.menu.module.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("message_essences")
public class MessageEssence {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private Long messageId;
    private Long createdByUserId;
    private LocalDateTime pinnedAt;
    private LocalDateTime createdAt;
}
