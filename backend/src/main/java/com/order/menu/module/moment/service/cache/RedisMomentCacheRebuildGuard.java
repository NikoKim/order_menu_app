package com.order.menu.module.moment.service.cache;

import com.order.menu.common.redis.RedisFailurePolicy;
import com.order.menu.config.OrderMenuRedisProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public class RedisMomentCacheRebuildGuard implements MomentCacheRebuildGuard {

    private static final Duration MAX_LEASE_TIME = Duration.ofSeconds(30);
    private static final Pattern RESOURCE_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final RedisFailurePolicy failurePolicy;
    private final String keyPrefix;

    public RedisMomentCacheRebuildGuard(
            StringRedisTemplate redis,
            RedisFailurePolicy failurePolicy,
            OrderMenuRedisProperties properties
    ) {
        this.redis = redis;
        this.failurePolicy = failurePolicy;
        this.keyPrefix = "oma:" + properties.getEnvironment() + ":moment:rebuild-lock:v1:";
    }

    @Override
    public Lease tryAcquire(String resource, String identity, Duration leaseTime) {
        validate(resource, leaseTime);
        String key = lockKey(resource, identity);
        String token = UUID.randomUUID().toString();
        return failurePolicy.execute(
                "moment-rebuild-lock",
                "acquire",
                key,
                () -> Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, leaseTime))
                        ? Lease.acquired(key, token)
                        : Lease.busy(),
                Lease.bypass()
        );
    }

    @Override
    public void release(Lease lease) {
        if (lease == null || !lease.acquired()
                || lease.lockKey() == null || lease.token() == null) {
            return;
        }
        failurePolicy.execute(
                "moment-rebuild-lock",
                "release",
                lease.lockKey(),
                () -> redis.execute(RELEASE_SCRIPT, List.of(lease.lockKey()), lease.token())
        );
    }

    private String lockKey(String resource, String identity) {
        String normalizedResource = resource.toLowerCase(Locale.ROOT);
        return keyPrefix + normalizedResource + ":" + hash(identity == null ? "" : identity);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void validate(String resource, Duration leaseTime) {
        if (resource == null || !RESOURCE_PATTERN.matcher(resource.toLowerCase(Locale.ROOT)).matches()) {
            throw new IllegalArgumentException("resource must contain only lower-case letters, digits and hyphens");
        }
        if (leaseTime == null || leaseTime.isZero() || leaseTime.isNegative()
                || leaseTime.compareTo(MAX_LEASE_TIME) > 0) {
            throw new IllegalArgumentException("leaseTime must be between 1 ms and 30 seconds");
        }
    }
}
