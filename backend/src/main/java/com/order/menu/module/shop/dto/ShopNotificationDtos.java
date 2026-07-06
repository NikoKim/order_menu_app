package com.order.menu.module.shop.dto;

public final class ShopNotificationDtos {
    private ShopNotificationDtos() {
    }

    public record ShopNotificationResponse(Long pendingOrders, Long pendingFamilyMembers) {
    }
}
