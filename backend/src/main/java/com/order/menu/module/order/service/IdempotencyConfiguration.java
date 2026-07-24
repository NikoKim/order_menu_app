package com.order.menu.module.order.service;

import com.order.menu.common.redis.RedisKeyFactory;
import com.order.menu.common.redis.RedisFailurePolicy;
import com.order.menu.config.OrderMenuRedisProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class IdempotencyConfiguration {

    @Bean
    @ConditionalOnExpression(
            "'${order-menu.redis.enabled:false}' == 'true' "
            + "and '${order-menu.redis.idempotency-enabled:false}' == 'true'"
    )
    public IdempotencyService idempotencyService(
            StringRedisTemplate redisTemplate,
            RedisKeyFactory keyFactory,
            OrderMenuRedisProperties properties,
            RedisFailurePolicy failurePolicy
    ) {
        return new IdempotencyService(redisTemplate, keyFactory, properties, failurePolicy);
    }
}
