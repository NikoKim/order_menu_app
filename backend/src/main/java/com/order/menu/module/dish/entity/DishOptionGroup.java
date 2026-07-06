package com.order.menu.module.dish.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("dish_option_groups")
public class DishOptionGroup {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long dishId;
    private String name;
    private Integer required;
    private String selectionType;
    private Integer minSelect;
    private Integer maxSelect;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
