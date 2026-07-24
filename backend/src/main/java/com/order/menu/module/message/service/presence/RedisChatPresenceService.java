package com.order.menu.module.message.service.presence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.common.redis.RedisKeyFactory;
import com.order.menu.common.redis.RedisFailurePolicy;
import com.order.menu.config.RedisRealtimeProperties;
import com.order.menu.module.message.entity.ConversationMember;
import com.order.menu.module.message.mapper.ConversationMemberMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

@Slf4j
@RequiredArgsConstructor
public class RedisChatPresenceService implements ChatPresenceService {

    private static final String ACTIVE = "ACTIVE";

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory keyFactory;
    private final RedisRealtimeProperties properties;
    private final ConversationMemberMapper conversationMemberMapper;
    private final RedisFailurePolicy failurePolicy;

    @Override
    public void heartbeat(Long userId) {
        String key = keyFactory.userOnline(userId);
        failurePolicy.execute("presence", "heartbeat", key, () -> {
            redisTemplate.opsForValue().set(
                    key,
                    "1",
                    Duration.ofSeconds(properties.getOnlineTtlSeconds()));
        });
    }

    @Override
    public boolean isOnline(Long userId) {
        String key = keyFactory.userOnline(userId);
        return failurePolicy.execute("presence", "online-lookup", key,
                () -> Boolean.TRUE.equals(redisTemplate.hasKey(key)), false);
    }

    @Override
    public void setTyping(Long userId, Long conversationId, boolean active) {
        requireActiveMember(userId, conversationId);
        String key = keyFactory.conversationTyping(conversationId);
        failurePolicy.execute("presence", "typing-update", key, () -> {
            if (!active) {
                redisTemplate.opsForZSet().remove(key, userId.toString());
                return;
            }
            long expiresAt = Instant.now().plusSeconds(properties.getTypingTtlSeconds()).toEpochMilli();
            redisTemplate.opsForZSet().add(key, userId.toString(), expiresAt);
            redisTemplate.expire(key, Duration.ofSeconds(properties.getTypingTtlSeconds() * 2));
        });
    }

    @Override
    public Set<Long> typingUsers(Long userId, Long conversationId) {
        requireActiveMember(userId, conversationId);
        String key = keyFactory.conversationTyping(conversationId);
        long now = Instant.now().toEpochMilli();
        return failurePolicy.execute("presence", "typing-lookup", key, () -> {
            redisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, now);
            Set<String> members = redisTemplate.opsForZSet()
                    .rangeByScore(key, Math.nextUp((double) now), Double.POSITIVE_INFINITY);
            if (members == null || members.isEmpty()) {
                return Set.of();
            }
            Set<Long> result = new LinkedHashSet<>();
            for (String member : members) {
                try {
                    Long typingUserId = Long.valueOf(member);
                    if (!typingUserId.equals(userId)) {
                        result.add(typingUserId);
                    }
                } catch (NumberFormatException ignored) {
                    log.debug("Ignoring malformed typing member in {}", key);
                }
            }
            return Collections.unmodifiableSet(result);
        }, Set.of());
    }

    private void requireActiveMember(Long userId, Long conversationId) {
        Long count = conversationMemberMapper.selectCount(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, conversationId)
                        .eq(ConversationMember::getUserId, userId)
                        .eq(ConversationMember::getMemberStatus, ACTIVE));
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该会话状态");
        }
    }
}
