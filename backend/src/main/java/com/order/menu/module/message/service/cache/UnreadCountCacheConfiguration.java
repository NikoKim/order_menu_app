package com.order.menu.module.message.service.cache;

import com.order.menu.common.redis.RedisKeyFactory;
import com.order.menu.common.redis.RedisFailurePolicy;
import com.order.menu.config.OrderMenuRedisProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class UnreadCountCacheConfiguration {

    @Bean
    @ConditionalOnExpression(
            "'${order-menu.redis.enabled:false}' == 'true' "
            + "and '${order-menu.redis.unread-cache-enabled:false}' == 'true'"
    )
    public UnreadCountCache redisUnreadCountCache(
            StringRedisTemplate redisTemplate,
            RedisKeyFactory keyFactory,
            OrderMenuRedisProperties properties,
            RedisFailurePolicy failurePolicy
    ) {
        return new RedisUnreadCountCache(redisTemplate, keyFactory, properties, failurePolicy);
    }

    @Bean
    @ConditionalOnMissingBean(UnreadCountCache.class)
    public UnreadCountCache noopUnreadCountCache() {
        return new NoopUnreadCountCache();
    }
}
