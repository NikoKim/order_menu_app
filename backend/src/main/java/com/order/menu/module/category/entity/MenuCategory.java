package com.order.menu.module.category.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("menu_categories")
public class MenuCategory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long shopId;
    private String name;
    private Integer sortOrder;

    @TableLogic
    private Integer deleted;

    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
