package com.order.menu.common.ratelimit;

import com.order.menu.common.redis.RedisKeyFactory;
import com.order.menu.common.redis.RedisFailurePolicy;
import com.order.menu.config.RedisRealtimeProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@ConditionalOnExpression("'${order-menu.redis.enabled:false}' == 'true' && "
        + "'${order-menu.redis.realtime.rate-limit-enabled:false}' == 'true'")
public class RateLimitConfiguration {

    @Bean
    RateLimiter redisRateLimiter(StringRedisTemplate redisTemplate, RedisKeyFactory keyFactory,
                                 RedisFailurePolicy failurePolicy) {
        return new RedisRateLimiter(redisTemplate, keyFactory, failurePolicy);
    }

    @Bean
    CriticalApiRateLimitAspect criticalApiRateLimitAspect(
            RateLimiter rateLimiter,
            RedisRealtimeProperties properties) {
        return new CriticalApiRateLimitAspect(rateLimiter, properties);
    }
}
