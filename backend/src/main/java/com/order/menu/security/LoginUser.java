package com.order.menu.security;

public record LoginUser(Long userId, String nickname, String role) {
    public boolean isCustomer() {
        return "CUSTOMER".equals(role);
    }

    public boolean isShop() {
        return "SHOP".equals(role);
    }
}
