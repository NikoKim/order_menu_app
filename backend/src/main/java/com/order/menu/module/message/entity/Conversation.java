package com.order.menu.module.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("conversations")
public class Conversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;
    private String directKey;
    private Long shopId;
    private Long ownerUserId;
    private String name;
    private String coverImageUrl;
    private String announcement;
    private Boolean joinApprovalRequired;
    private String status;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
