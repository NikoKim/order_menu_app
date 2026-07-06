package com.order.menu.module.order.controller;

import com.order.menu.common.response.ApiResponse;
import com.order.menu.common.response.PageResponse;
import com.order.menu.common.util.PageUtils;
import com.order.menu.module.order.dto.OrderDtos;
import com.order.menu.module.order.service.OrderService;
import com.order.menu.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @PostMapping
    public ApiResponse<OrderDtos.OrderSubmitResponse> create(@Valid @RequestBody OrderDtos.CreateOrderRequest request) {
        return ApiResponse.success(orderService.create(UserContext.get().userId(), request));
    }

    @GetMapping("/my")
    public ApiResponse<PageResponse<OrderDtos.OrderSummaryResponse>> my(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(orderService.myOrders(UserContext.get().userId(), PageUtils.page(page), PageUtils.pageSize(pageSize), status));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDtos.OrderDetailResponse> detail(@PathVariable Long orderId) {
        return ApiResponse.success(orderService.customerDetail(UserContext.get().userId(), orderId));
    }

    @PutMapping("/{orderId}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long orderId) {
        orderService.cancel(UserContext.get().userId(), orderId);
        return ApiResponse.success();
    }
}
