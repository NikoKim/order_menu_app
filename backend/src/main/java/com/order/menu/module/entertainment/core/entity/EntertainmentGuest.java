package com.order.menu.module.entertainment.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("entertainment_guests")
public class EntertainmentGuest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String publicIdHash;
    private String displaySuffix;
    private String status;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
}
