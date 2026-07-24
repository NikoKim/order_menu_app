package com.order.menu.module.message.service.cache;

import com.order.menu.common.redis.RedisJsonCodec;
import com.order.menu.common.redis.RedisKeyFactory;
import com.order.menu.common.redis.RedisFailurePolicy;
import com.order.menu.config.OrderMenuRedisProperties;
import com.order.menu.module.message.dto.ChatMessageCacheDto;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class RedisChatCache implements ChatCachePort {

    private static final int MAX_RECENT_IDS = 200;

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory keyFactory;
    private final RedisJsonCodec codec;
    private final OrderMenuRedisProperties properties;
    private final RedisFailurePolicy failurePolicy;

    public RedisChatCache(StringRedisTemplate redisTemplate,
                          RedisKeyFactory keyFactory,
                          RedisJsonCodec codec,
                          OrderMenuRedisProperties properties,
                          RedisFailurePolicy failurePolicy) {
        this.redisTemplate = redisTemplate;
        this.keyFactory = keyFactory;
        this.codec = codec;
        this.properties = properties;
        this.failurePolicy = failurePolicy;
    }

    @Override
    public Optional<List<ChatMessageCacheDto>> getRecentMessages(Long conversationId, int limit) {
        String recentKey = keyFactory.chatRecentIds(conversationId);
        Optional<List<ChatMessageCacheDto>> result = failurePolicy.execute("chat", "read-recent", recentKey, () -> {
            Set<String> ids = redisTemplate.opsForZSet().reverseRange(recentKey, 0, limit - 1);
            if (ids == null || ids.isEmpty()) {
                return Optional.empty();
            }
            List<String> messageKeys = ids.stream()
                    .map(id -> keyFactory.chatMessage(Long.valueOf(id)))
                    .toList();
            List<String> values = redisTemplate.opsForValue().multiGet(messageKeys);
            if (values == null || values.stream().anyMatch(v -> v == null)) {
                return Optional.empty();
            }
            List<ChatMessageCacheDto> messages = new ArrayList<>();
            for (String value : values) {
                messages.add(codec.read(value, ChatMessageCacheDto.class));
            }
            return Optional.of(messages);
        }, Optional.empty());
        failurePolicy.recordCacheResult("chat", result.isPresent());
        return result;
    }

    @Override
    public void cacheRecentMessages(Long conversationId, List<ChatMessageCacheDto> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        String recentKey = keyFactory.chatRecentIds(conversationId);
        long ttlSeconds = properties.getChatRecentTtlSeconds()
                + ThreadLocalRandom.current().nextLong(0, 31);
        failurePolicy.execute("chat", "write-recent", recentKey, () -> {
            for (ChatMessageCacheDto msg : messages) {
                String msgKey = keyFactory.chatMessage(msg.id());
                redisTemplate.opsForValue().set(msgKey, codec.write(msg),
                        Duration.ofSeconds(properties.getChatMessageTtlSeconds()
                                + ThreadLocalRandom.current().nextLong(0, 31)));
                redisTemplate.opsForZSet().add(recentKey, String.valueOf(msg.id()), msg.id());
            }
            redisTemplate.opsForZSet().removeRange(recentKey, 0, -(MAX_RECENT_IDS + 1));
            redisTemplate.expire(recentKey, Duration.ofSeconds(ttlSeconds));
        });
    }

    @Override
    public void appendMessage(ChatMessageCacheDto message) {
        String recentKey = keyFactory.chatRecentIds(message.conversationId());
        failurePolicy.execute("chat", "append", recentKey, () -> {
            String msgKey = keyFactory.chatMessage(message.id());
            redisTemplate.opsForValue().set(msgKey, codec.write(message),
                    Duration.ofSeconds(properties.getChatMessageTtlSeconds()
                            + ThreadLocalRandom.current().nextLong(0, 31)));
            redisTemplate.opsForZSet().add(recentKey, String.valueOf(message.id()), message.id());
            redisTemplate.opsForZSet().removeRange(recentKey, 0, -(MAX_RECENT_IDS + 1));
            redisTemplate.expire(recentKey,
                    Duration.ofSeconds(properties.getChatRecentTtlSeconds()
                            + ThreadLocalRandom.current().nextLong(0, 31)));
        });
    }

    @Override
    public void evictMessage(Long conversationId, Long messageId) {
        failurePolicy.execute("chat", "evict-message", keyFactory.chatMessage(messageId), () -> {
            redisTemplate.delete(keyFactory.chatMessage(messageId));
            redisTemplate.opsForZSet().remove(
                    keyFactory.chatRecentIds(conversationId),
                    String.valueOf(messageId));
        });
    }
}
