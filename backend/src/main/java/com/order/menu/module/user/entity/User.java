package com.order.menu.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("users")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String nickname;
    private String passwordHash;
    private String role;
    private String avatarUrl;
    private String phone;
    private Integer status;
    private Boolean allowPrivateChat = true;
    private Boolean autoJoinFamilyChat = true;
    private Boolean showJoinedFamilies = true;
    private Boolean showPhone = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
