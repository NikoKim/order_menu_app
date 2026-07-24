package com.order.menu.module.message.service.presence;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.redis.RedisKeyFactory;
import com.order.menu.common.redis.RedisFailurePolicy;
import com.order.menu.config.RedisRealtimeProperties;
import com.order.menu.module.message.entity.ConversationMember;
import com.order.menu.module.message.mapper.ConversationMemberMapper;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisChatPresenceServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ZSetOperations<String, String> zSetOperations;
    private ConversationMemberMapper memberMapper;
    private RedisChatPresenceService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        zSetOperations = mock(ZSetOperations.class);
        memberMapper = mock(ConversationMemberMapper.class);
        RedisKeyFactory keyFactory = mock(RedisKeyFactory.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(keyFactory.userOnline(7L)).thenReturn("online:7");
        when(keyFactory.conversationTyping(9L)).thenReturn("typing:9");
        RedisRealtimeProperties properties = new RedisRealtimeProperties();
        service = new RedisChatPresenceService(
                redisTemplate, keyFactory, properties, memberMapper,
                new RedisFailurePolicy(new SimpleMeterRegistry()));
    }

    @Test
    void heartbeatUsesConfiguredShortTtl() {
        service.heartbeat(7L);

        verify(valueOperations).set("online:7", "1", Duration.ofSeconds(90));
    }

    @Test
    void typingRequiresActiveConversationMembership() {
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        assertThatThrownBy(() -> service.setTyping(7L, 9L, true))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(403);
    }

    @Test
    void typingHeartbeatUsesExpiringSortedSetAndFiltersExpiredMembers() {
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(zSetOperations.rangeByScore(eq("typing:9"), anyDouble(), anyDouble()))
                .thenReturn(java.util.Set.of("7", "11", "invalid"));

        service.setTyping(7L, 9L, true);
        var users = service.typingUsers(7L, 9L);

        verify(zSetOperations).add(eq("typing:9"), eq("7"), anyDouble());
        verify(redisTemplate).expire("typing:9", Duration.ofSeconds(16));
        verify(zSetOperations).removeRangeByScore(eq("typing:9"), anyDouble(), anyDouble());
        assertThat(users).containsExactly(11L);
    }
}
