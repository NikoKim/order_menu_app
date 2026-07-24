package com.order.menu.module.moment.service;

import com.order.menu.common.redis.RedisJsonCodec;
import com.order.menu.common.redis.RedisKeyFactory;
import com.order.menu.common.redis.RedisFailurePolicy;
import com.order.menu.common.transaction.AfterCommitExecutor;
import com.order.menu.config.OrderMenuRedisProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class MomentCacheConfiguration {

    @Bean
    @ConditionalOnExpression(
            "'${order-menu.redis.enabled:false}' == 'true' "
            + "and '${order-menu.redis.moment-cache-enabled:false}' == 'true'"
    )
    public MomentCachePort redisMomentCache(
            StringRedisTemplate redisTemplate,
            RedisKeyFactory keyFactory,
            RedisJsonCodec codec,
            OrderMenuRedisProperties properties,
            AfterCommitExecutor afterCommitExecutor,
            RedisFailurePolicy failurePolicy
    ) {
        return new RedisMomentCacheAdapter(redisTemplate, keyFactory, codec, properties,
                afterCommitExecutor, failurePolicy);
    }

    @Bean
    @ConditionalOnMissingBean(MomentCachePort.class)
    public MomentCachePort noopMomentCache() {
        return new NoopMomentCache();
    }
}
