package com.order.menu.module.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("message_essence_likes")
public class MessageEssenceLike {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long essenceId;
    private Long userId;
    private LocalDateTime createdAt;
}
