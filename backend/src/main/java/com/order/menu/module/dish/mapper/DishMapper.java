package com.order.menu.module.dish.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.order.menu.module.dish.entity.Dish;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface DishMapper extends BaseMapper<Dish> {

    @Update("""
        UPDATE dishes
        SET stock = CASE
            WHEN stock IS NULL THEN NULL
            ELSE stock - #{quantity}
        END
        WHERE id = #{dishId}
          AND (stock IS NULL OR stock >= #{quantity})
        """)
    int deductStock(
            @Param("dishId") Long dishId,
            @Param("quantity") Integer quantity
    );

    @Update("""
        UPDATE dishes
        SET stock = CASE
            WHEN stock IS NULL THEN NULL
            ELSE stock + #{quantity}
        END
        WHERE id = #{dishId}
        """)
    int restoreStock(
            @Param("dishId") Long dishId,
            @Param("quantity") Integer quantity
    );
}
