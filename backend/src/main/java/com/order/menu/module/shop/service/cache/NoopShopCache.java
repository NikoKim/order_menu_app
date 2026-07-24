package com.order.menu.module.shop.service.cache;

import com.order.menu.module.shop.dto.ShopDtos;

import java.util.Optional;

public class NoopShopCache implements ShopCachePort {

    @Override
    public Optional<ShopDtos.ShopResponse> getShopDetail(Long shopId) {
        return Optional.empty();
    }

    @Override
    public void putShopDetail(Long shopId, ShopDtos.ShopResponse response) {
    }

    @Override
    public Optional<ShopDtos.MenuResponse> getMenu(Long shopId) {
        return Optional.empty();
    }

    @Override
    public void putMenu(Long shopId, ShopDtos.MenuResponse response) {
    }

    @Override
    public void evictShopDetail(Long shopId) {
    }

    @Override
    public void evictMenu(Long shopId) {
    }
}
