package com.order.menu.module.shop.service.cache;

import com.order.menu.module.shop.dto.ShopDtos;

import java.util.Optional;

public interface ShopCachePort {

    Optional<ShopDtos.ShopResponse> getShopDetail(Long shopId);

    void putShopDetail(Long shopId, ShopDtos.ShopResponse response);

    Optional<ShopDtos.MenuResponse> getMenu(Long shopId);

    void putMenu(Long shopId, ShopDtos.MenuResponse response);

    void evictShopDetail(Long shopId);

    void evictMenu(Long shopId);
}
