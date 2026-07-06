package com.order.menu.module.dish.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("dish_option_items")
public class DishOptionItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long groupId;
    private String name;
    private BigDecimal priceDelta;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
