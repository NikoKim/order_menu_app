package com.order.menu.module.shop.service.cache;

import com.order.menu.common.redis.RedisJsonCodec;
import com.order.menu.common.redis.RedisKeyFactory;
import com.order.menu.common.redis.RedisFailurePolicy;
import com.order.menu.config.OrderMenuRedisProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class ShopCacheConfiguration {

    @Bean
    @ConditionalOnExpression(
            "'${order-menu.redis.enabled:false}' == 'true' "
            + "and '${order-menu.redis.menu-cache-enabled:false}' == 'true'"
    )
    public ShopCachePort redisShopCache(
            StringRedisTemplate redisTemplate,
            RedisKeyFactory keyFactory,
            RedisJsonCodec codec,
            OrderMenuRedisProperties properties,
            RedisFailurePolicy failurePolicy
    ) {
        return new RedisShopCache(redisTemplate, keyFactory, codec, properties, failurePolicy);
    }

    @Bean
    @ConditionalOnMissingBean(ShopCachePort.class)
    public ShopCachePort noopShopCache() {
        return new NoopShopCache();
    }
}
