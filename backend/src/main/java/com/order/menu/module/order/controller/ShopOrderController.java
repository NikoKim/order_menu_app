package com.order.menu.module.order.controller;

import com.order.menu.common.response.ApiResponse;
import com.order.menu.common.response.PageResponse;
import com.order.menu.common.util.PageUtils;
import com.order.menu.module.order.dto.OrderDtos;
import com.order.menu.module.order.service.OrderService;
import com.order.menu.security.UserContext;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shop/orders")
@RequiredArgsConstructor
public class ShopOrderController {
    private final OrderService orderService;

    @GetMapping
    public ApiResponse<PageResponse<OrderDtos.OrderSummaryResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(orderService.shopOrders(UserContext.get().userId(), PageUtils.page(page), PageUtils.pageSize(pageSize), status, date));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDtos.OrderDetailResponse> detail(@PathVariable Long orderId) {
        return ApiResponse.success(orderService.shopDetail(UserContext.get().userId(), orderId));
    }

    @PutMapping("/{orderId}/accept")
    public ApiResponse<Void> accept(@PathVariable Long orderId) {
        orderService.accept(UserContext.get().userId(), orderId);
        return ApiResponse.success();
    }

    @PutMapping("/{orderId}/reject")
    public ApiResponse<Void> reject(@PathVariable Long orderId) {
        orderService.reject(UserContext.get().userId(), orderId);
        return ApiResponse.success();
    }

    @PutMapping("/{orderId}/complete")
    public ApiResponse<Void> complete(@PathVariable Long orderId) {
        orderService.complete(UserContext.get().userId(), orderId);
        return ApiResponse.success();
    }
}
