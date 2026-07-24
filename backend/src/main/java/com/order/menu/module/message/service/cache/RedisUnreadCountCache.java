package com.order.menu.module.message.service.cache;

import com.order.menu.common.redis.RedisKeyFactory;
import com.order.menu.common.redis.RedisFailurePolicy;
import com.order.menu.config.OrderMenuRedisProperties;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class RedisUnreadCountCache implements UnreadCountCache {

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory keyFactory;
    private final OrderMenuRedisProperties properties;
    private final RedisFailurePolicy failurePolicy;

    public RedisUnreadCountCache(StringRedisTemplate redisTemplate,
                                 RedisKeyFactory keyFactory,
                                 OrderMenuRedisProperties properties,
                                 RedisFailurePolicy failurePolicy) {
        this.redisTemplate = redisTemplate;
        this.keyFactory = keyFactory;
        this.properties = properties;
        this.failurePolicy = failurePolicy;
    }

    private long ttlSeconds() {
        return properties.getUnreadCountTtlSeconds() + ThreadLocalRandom.current().nextLong(0, 31);
    }

    @Override
    public Optional<Long> getNotificationUnread(Long userId) {
        return get(keyFactory.notificationUnread(userId));
    }

    @Override
    public void putNotificationUnread(Long userId, long count) {
        put(keyFactory.notificationUnread(userId), count);
    }

    @Override
    public void evictNotificationUnread(Long userId) {
        del(keyFactory.notificationUnread(userId));
    }

    @Override
    public Optional<Long> getChatUnread(Long userId, Long conversationId) {
        return get(keyFactory.chatUnread(userId, conversationId));
    }

    @Override
    public void putChatUnread(Long userId, Long conversationId, long count) {
        put(keyFactory.chatUnread(userId, conversationId), count);
    }

    @Override
    public void evictChatUnread(Long userId, Long conversationId) {
        del(keyFactory.chatUnread(userId, conversationId));
    }

    @Override
    public Optional<Long> getChatUnreadTotal(Long userId) {
        return get(keyFactory.chatUnreadTotal(userId));
    }

    @Override
    public void putChatUnreadTotal(Long userId, long count) {
        put(keyFactory.chatUnreadTotal(userId), count);
    }

    @Override
    public void evictChatUnreadTotal(Long userId) {
        del(keyFactory.chatUnreadTotal(userId));
    }

    private Optional<Long> get(String key) {
        Optional<Long> result = failurePolicy.execute("unread", "read", key, () -> {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) return Optional.empty();
            return Optional.of(Long.parseLong(value));
        }, Optional.empty());
        failurePolicy.recordCacheResult("unread", result.isPresent());
        return result;
    }

    private void put(String key, long count) {
        failurePolicy.execute("unread", "write", key, () -> {
            redisTemplate.opsForValue().set(key, String.valueOf(count), Duration.ofSeconds(ttlSeconds()));
        });
    }

    private void del(String key) {
        failurePolicy.execute("unread", "evict", key, () -> { redisTemplate.delete(key); });
    }
}
