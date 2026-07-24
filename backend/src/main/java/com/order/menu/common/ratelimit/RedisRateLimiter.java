package com.order.menu.common.ratelimit;

import com.order.menu.common.redis.RedisKeyFactory;
import com.order.menu.common.redis.RedisFailurePolicy;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@RequiredArgsConstructor
public class RedisRateLimiter implements RateLimiter {

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory keyFactory;
    private final RedisFailurePolicy failurePolicy;

    @Override
    public boolean tryAcquire(String action, String subject, int limit, Duration window) {
        if (limit <= 0 || window.isZero() || window.isNegative()) {
            return true;
        }
        String key = keyFactory.rateLimit(action, subject);
        Long current = failurePolicy.execute("rate-limit", "acquire", key, () -> redisTemplate.execute(
                    SCRIPT,
                    List.of(key),
                    Long.toString(window.toMillis())), null);
        return current == null || current <= limit;
    }
}
