package com.order.menu.module.moment.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;
@Data @TableName("moments")
public class Moment { @TableId(type=IdType.AUTO) private Long id; private Long authorUserId; private String title; private String content; private String visibility; private String status; private Long shopId; private Long dishId; private String imageUrls; private Long shareCount; private Long appShareCount; private Long externalShareCount; private LocalDateTime editedAt; private LocalDateTime publishedAt; private LocalDateTime createdAt; private LocalDateTime updatedAt; }
