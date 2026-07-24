package com.order.menu.module.shop.service.cache;

import com.order.menu.common.redis.RedisJsonCodec;
import com.order.menu.common.redis.RedisKeyFactory;
import com.order.menu.common.redis.RedisFailurePolicy;
import com.order.menu.config.OrderMenuRedisProperties;
import com.order.menu.module.shop.dto.ShopDtos;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class RedisShopCache implements ShopCachePort {

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory keyFactory;
    private final RedisJsonCodec codec;
    private final OrderMenuRedisProperties properties;
    private final RedisFailurePolicy failurePolicy;

    public RedisShopCache(StringRedisTemplate redisTemplate,
                          RedisKeyFactory keyFactory,
                          RedisJsonCodec codec,
                          OrderMenuRedisProperties properties,
                          RedisFailurePolicy failurePolicy) {
        this.redisTemplate = redisTemplate;
        this.keyFactory = keyFactory;
        this.codec = codec;
        this.properties = properties;
        this.failurePolicy = failurePolicy;
    }

    @Override
    public Optional<ShopDtos.ShopResponse> getShopDetail(Long shopId) {
        String key = keyFactory.shopDetail(shopId);
        Optional<ShopDtos.ShopResponse> result = failurePolicy.execute("menu", "read-detail", key, () -> {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(codec.read(value, ShopDtos.ShopResponse.class));
        }, Optional.empty());
        failurePolicy.recordCacheResult("shop-detail", result.isPresent());
        return result;
    }

    @Override
    public void putShopDetail(Long shopId, ShopDtos.ShopResponse response) {
        String key = keyFactory.shopDetail(shopId);
        failurePolicy.execute("menu", "write-detail", key, () -> {
            long seconds = properties.getShopDetailTtlSeconds()
                    + ThreadLocalRandom.current().nextLong(0, 31);
            redisTemplate.opsForValue().set(key, codec.write(response), Duration.ofSeconds(seconds));
        });
    }

    @Override
    public Optional<ShopDtos.MenuResponse> getMenu(Long shopId) {
        String key = keyFactory.shopMenu(shopId);
        Optional<ShopDtos.MenuResponse> result = failurePolicy.execute("menu", "read-menu", key, () -> {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(codec.read(value, ShopDtos.MenuResponse.class));
        }, Optional.empty());
        failurePolicy.recordCacheResult("menu", result.isPresent());
        return result;
    }

    @Override
    public void putMenu(Long shopId, ShopDtos.MenuResponse response) {
        String key = keyFactory.shopMenu(shopId);
        failurePolicy.execute("menu", "write-menu", key, () -> {
            long seconds = properties.getShopMenuTtlSeconds()
                    + ThreadLocalRandom.current().nextLong(0, 31);
            redisTemplate.opsForValue().set(key, codec.write(response), Duration.ofSeconds(seconds));
        });
    }

    @Override
    public void evictShopDetail(Long shopId) {
        String key = keyFactory.shopDetail(shopId);
        failurePolicy.execute("menu", "evict-detail", key, () -> { redisTemplate.delete(key); });
    }

    @Override
    public void evictMenu(Long shopId) {
        String key = keyFactory.shopMenu(shopId);
        failurePolicy.execute("menu", "evict-menu", key, () -> { redisTemplate.delete(key); });
    }
}
