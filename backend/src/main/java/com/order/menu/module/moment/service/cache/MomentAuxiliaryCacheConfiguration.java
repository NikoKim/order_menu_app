package com.order.menu.module.moment.service.cache;

import com.order.menu.common.redis.RedisFailurePolicy;
import com.order.menu.common.redis.RedisJsonCodec;
import com.order.menu.common.transaction.AfterCommitExecutor;
import com.order.menu.config.OrderMenuRedisProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class MomentAuxiliaryCacheConfiguration {

    @Bean
    @ConditionalOnExpression(
            "'${order-menu.redis.enabled:false}' == 'true' "
                    + "and '${order-menu.redis.moment-cache-enabled:false}' == 'true'"
    )
    public MomentAuxiliaryCachePort redisMomentAuxiliaryCache(
            StringRedisTemplate redis,
            RedisJsonCodec codec,
            RedisFailurePolicy failurePolicy,
            AfterCommitExecutor afterCommitExecutor,
            OrderMenuRedisProperties properties
    ) {
        return new RedisMomentAuxiliaryCache(redis, codec, failurePolicy, afterCommitExecutor, properties);
    }

    @Bean
    @ConditionalOnMissingBean(MomentAuxiliaryCachePort.class)
    public MomentAuxiliaryCachePort noopMomentAuxiliaryCache() {
        return new NoopMomentAuxiliaryCache();
    }
}
