package com.order.menu.module.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class OrderDtos {
    private OrderDtos() {
    }

    public record CreateOrderRequest(
            @NotBlank(message = "clientRequestId不能为空")
            @Size(max = 64, message = "clientRequestId长度不能超过64")
            String clientRequestId,
            @NotNull(message = "商家ID不能为空") Long shopId,
            @Size(max = 500, message = "备注不能超过500个字符") String remark,
            @NotEmpty(message = "订单菜品不能为空") List<@Valid CreateOrderItemRequest> items
    ) {
    }

    public record CreateOrderItemRequest(
            @NotNull(message = "菜品ID不能为空") Long dishId,
            @NotNull(message = "数量不能为空") @Positive(message = "数量必须大于0") Integer quantity,
            List<Long> optionItemIds
    ) {
    }

    public record OrderSubmitResponse(Long orderId, String orderNo, String status, BigDecimal totalAmount) {
    }

    public record OrderSummaryResponse(Long id, String orderNo, Long shopId, String shopName, String status,
                                       BigDecimal totalAmount, LocalDateTime createdAt) {
    }

    public record OrderDetailResponse(Long id, String orderNo, Long shopId, String shopName, Long customerUserId,
                                      String customerNickname, String customerPhone, String customerAvatarUrl,
                                      Boolean familyCustomer, String status, BigDecimal totalAmount, String remark,
                                      LocalDateTime createdAt, List<OrderItemResponse> items) {
    }

    public record OrderItemResponse(Long dishId, String dishName, String dishImageUrl, BigDecimal unitPrice,
                                    Integer quantity, BigDecimal subtotalAmount, String selectedOptions,
                                    BigDecimal optionAmount) {
    }
}
