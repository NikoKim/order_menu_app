package com.order.menu.module.shop.service.cache;

import com.order.menu.common.transaction.AfterCommitExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MenuCacheInvalidator {

    private final ShopCachePort shopCachePort;
    private final AfterCommitExecutor afterCommitExecutor;

    public void evictMenuAfterCommit(Long shopId) {
        afterCommitExecutor.execute(() -> shopCachePort.evictMenu(shopId));
    }

    public void evictShopAfterCommit(Long shopId) {
        afterCommitExecutor.execute(() -> {
            shopCachePort.evictShopDetail(shopId);
            shopCachePort.evictMenu(shopId);
        });
    }
}
