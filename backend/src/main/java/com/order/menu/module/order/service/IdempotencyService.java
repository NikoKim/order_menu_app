package com.order.menu.module.order.service;

import com.order.menu.common.redis.RedisKeyFactory;
import com.order.menu.common.redis.RedisFailurePolicy;
import com.order.menu.config.OrderMenuRedisProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public class IdempotencyService {

    private static final String PENDING_PREFIX = "PENDING:";
    private static final String SUCCESS_PREFIX = "SUCCESS:";
    private static final DefaultRedisScript<Long> COMPLETE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
              return 1
            end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory keyFactory;
    private final OrderMenuRedisProperties properties;
    private final RedisFailurePolicy failurePolicy;

    public IdempotencyService(StringRedisTemplate redisTemplate,
                              RedisKeyFactory keyFactory,
                              OrderMenuRedisProperties properties,
                              RedisFailurePolicy failurePolicy) {
        this.redisTemplate = redisTemplate;
        this.keyFactory = keyFactory;
        this.properties = properties;
        this.failurePolicy = failurePolicy;
    }

    public AcquireResult acquire(Long userId, String clientRequestId) {
        String key = keyFactory.orderIdempotency(userId, clientRequestId);
        return failurePolicy.execute("idempotency", "acquire", key, () -> {
            String existing = redisTemplate.opsForValue().get(key);
            if (existing != null) {
                if (existing.startsWith(SUCCESS_PREFIX)) {
                    Long orderId = Long.valueOf(existing.substring(SUCCESS_PREFIX.length()));
                    return AcquireResult.existing(orderId);
                }
                return AcquireResult.pending();
            }
            String pendingToken = UUID.randomUUID().toString();
            String pendingValue = PENDING_PREFIX + pendingToken;
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    key, pendingValue,
                    Duration.ofSeconds(properties.getIdempotencyPendingTtlSeconds()));
            if (Boolean.TRUE.equals(acquired)) {
                return AcquireResult.acquired(pendingToken);
            }
            String afterAcquire = redisTemplate.opsForValue().get(key);
            if (afterAcquire != null && afterAcquire.startsWith(SUCCESS_PREFIX)) {
                Long orderId = Long.valueOf(afterAcquire.substring(SUCCESS_PREFIX.length()));
                return AcquireResult.existing(orderId);
            }
            return AcquireResult.pending();
        }, AcquireResult.fallback());
    }

    public void markSuccess(Long userId, String clientRequestId, Long orderId) {
        String key = keyFactory.orderIdempotency(userId, clientRequestId);
        failurePolicy.execute("idempotency", "mark-success", key, () -> {
            redisTemplate.opsForValue().set(key, SUCCESS_PREFIX + orderId,
                    Duration.ofSeconds(properties.getIdempotencySuccessTtlSeconds()));
        });
    }

    public void markSuccess(Long userId, String clientRequestId, Long orderId, String pendingToken) {
        String key = keyFactory.orderIdempotency(userId, clientRequestId);
        failurePolicy.execute("idempotency", "complete", key, () -> {
            redisTemplate.execute(COMPLETE_SCRIPT, List.of(key),
                    PENDING_PREFIX + pendingToken,
                    SUCCESS_PREFIX + orderId,
                    String.valueOf(properties.getIdempotencySuccessTtlSeconds()));
        });
    }

    public void releasePending(Long userId, String clientRequestId, String pendingToken) {
        String key = keyFactory.orderIdempotency(userId, clientRequestId);
        failurePolicy.execute("idempotency", "release", key, () -> {
            redisTemplate.execute(RELEASE_SCRIPT, List.of(key), PENDING_PREFIX + pendingToken);
        });
    }

    public record AcquireResult(Type type, Long existingOrderId, String pendingToken) {
        public enum Type { ACQUIRED, EXISTING, PENDING, FALLBACK }

        public static AcquireResult acquired(String pendingToken) { return new AcquireResult(Type.ACQUIRED, null, pendingToken); }
        public static AcquireResult existing(Long orderId) { return new AcquireResult(Type.EXISTING, orderId, null); }
        public static AcquireResult pending() { return new AcquireResult(Type.PENDING, null, null); }
        public static AcquireResult fallback() { return new AcquireResult(Type.FALLBACK, null, null); }
    }
}
