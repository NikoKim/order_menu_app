package com.order.menu.module.shop.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.order.menu.common.constant.StatusConstants;
import com.order.menu.common.response.ApiResponse;
import com.order.menu.module.family.entity.FamilyMember;
import com.order.menu.module.family.mapper.FamilyMemberMapper;
import com.order.menu.module.order.entity.Order;
import com.order.menu.module.order.mapper.OrderMapper;
import com.order.menu.module.shop.dto.ShopNotificationDtos;
import com.order.menu.module.shop.entity.Shop;
import com.order.menu.module.shop.service.ShopService;
import com.order.menu.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shop/notifications")
@RequiredArgsConstructor
public class ShopNotificationController {
    private final ShopService shopService;
    private final OrderMapper orderMapper;
    private final FamilyMemberMapper familyMemberMapper;

    @GetMapping
    public ApiResponse<ShopNotificationDtos.ShopNotificationResponse> notifications() {
        Shop shop = shopService.requireOwnedShop(UserContext.get().userId());
        Long pendingOrders = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getShopId, shop.getId())
                .eq(Order::getStatus, StatusConstants.ORDER_SUBMITTED));
        Long pendingFamilyMembers = familyMemberMapper.selectCount(new LambdaQueryWrapper<FamilyMember>()
                .eq(FamilyMember::getShopId, shop.getId())
                .eq(FamilyMember::getStatus, "PENDING"));
        return ApiResponse.success(new ShopNotificationDtos.ShopNotificationResponse(pendingOrders, pendingFamilyMembers));
    }
}
