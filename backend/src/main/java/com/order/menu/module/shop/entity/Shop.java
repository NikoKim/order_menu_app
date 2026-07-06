package com.order.menu.module.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("shops")
public class Shop {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private String name;
    private String description;
    private String coverImageUrl;
    private String address;
    private String phone;
    private String businessStatus;
    private String visibilityType;
    private String familyName;
    private String familyCode;
    private String familyJoinMode;
    private String familyImageUrl;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
