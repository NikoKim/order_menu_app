package com.order.menu.module.moment.service.cache;

import com.order.menu.common.redis.RedisFailurePolicy;
import com.order.menu.config.OrderMenuRedisProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class MomentCacheRebuildGuardConfiguration {

    @Bean
    @ConditionalOnExpression(
            "'${order-menu.redis.enabled:false}' == 'true' "
                    + "and '${order-menu.redis.moment-cache-enabled:false}' == 'true'"
    )
    public MomentCacheRebuildGuard redisMomentCacheRebuildGuard(
            StringRedisTemplate redis,
            RedisFailurePolicy failurePolicy,
            OrderMenuRedisProperties properties
    ) {
        return new RedisMomentCacheRebuildGuard(redis, failurePolicy, properties);
    }

    @Bean
    @ConditionalOnMissingBean(MomentCacheRebuildGuard.class)
    public MomentCacheRebuildGuard noopMomentCacheRebuildGuard() {
        return new NoopMomentCacheRebuildGuard();
    }
}
