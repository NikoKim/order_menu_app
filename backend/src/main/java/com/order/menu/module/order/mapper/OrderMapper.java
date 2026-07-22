package com.order.menu.module.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.order.menu.module.order.entity.Order;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface OrderMapper extends BaseMapper<Order> {

    @Update("""
        UPDATE orders
        SET status = #{targetStatus},
            updated_at = NOW()
        WHERE id = #{orderId}
          AND status IN ('SUBMITTED', 'ACCEPTED')
        """)
    int changeToCancelledIfAllowed(
            @Param("orderId") Long orderId,
            @Param("targetStatus") String targetStatus
    );

    @Select("""
        SELECT * FROM orders
        WHERE customer_user_id = #{userId}
          AND client_request_id = #{clientRequestId}
        LIMIT 1
        """)
    Order selectByCustomerAndClientRequestId(
            @Param("userId") Long userId,
            @Param("clientRequestId") String clientRequestId
    );
}
