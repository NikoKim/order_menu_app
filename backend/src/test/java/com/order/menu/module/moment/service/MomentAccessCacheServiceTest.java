package com.order.menu.module.moment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.menu.common.redis.RedisFailurePolicy;
import com.order.menu.common.redis.RedisJsonCodec;
import com.order.menu.common.transaction.AfterCommitExecutor;
import com.order.menu.config.OrderMenuRedisProperties;
import com.order.menu.module.message.mapper.ChatMessageMapper;
import com.order.menu.module.moment.entity.MomentUserPreference;
import com.order.menu.module.moment.mapper.MomentUserPreferenceMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MomentAccessCacheServiceTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private MomentUserPreferenceMapper preferenceMapper;
    private ChatMessageMapper chatMessageMapper;
    private RedisJsonCodec codec;
    private MomentAccessCacheService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        preferenceMapper = mock(MomentUserPreferenceMapper.class);
        chatMessageMapper = mock(ChatMessageMapper.class);
        codec = new RedisJsonCodec(new ObjectMapper());
        OrderMenuRedisProperties properties = new OrderMenuRedisProperties();
        properties.setEnabled(true);
        properties.setMomentCacheEnabled(true);
        properties.setEnvironment("test");
        properties.setMomentUnreadTtlSeconds(30);
        service = new MomentAccessCacheService(
                redis,
                codec,
                new RedisFailurePolicy(new SimpleMeterRegistry()),
                new AfterCommitExecutor(),
                properties,
                preferenceMapper,
                chatMessageMapper);
    }

    @Test
    void cacheHitReturnsCombinedSnapshotWithoutDatabaseQueries() {
        MomentAccessSnapshot snapshot = new MomentAccessSnapshot(
                java.util.Set.of(2L), java.util.Set.of(3L, 4L));
        when(values.get("oma:test:moment:access:v1:1")).thenReturn(codec.write(snapshot));

        MomentAccessSnapshot result = service.accessFor(1L);

        assertThat(result.blockedAuthorIds()).containsExactly(2L);
        assertThat(result.relatedUserIds()).containsExactlyInAnyOrder(3L, 4L);
        verifyNoInteractions(preferenceMapper, chatMessageMapper);
    }

    @Test
    void cacheMissLoadsBothRelationshipSetsAndCachesEmptySafeJson() {
        MomentUserPreference hiddenByMe = preference(1L, 2L, true, false);
        MomentUserPreference hiddenFromMe = preference(3L, 1L, false, true);
        when(preferenceMapper.selectList(any())).thenReturn(List.of(hiddenByMe, hiddenFromMe));
        when(chatMessageMapper.selectEstablishedDirectChatUserIds(1L)).thenReturn(List.of(4L));

        MomentAccessSnapshot result = service.accessFor(1L);

        assertThat(result.blockedAuthorIds()).containsExactlyInAnyOrder(2L, 3L);
        assertThat(result.relatedUserIds()).containsExactly(4L);
        verify(values).set(
                eq("oma:test:moment:access:v1:1"),
                any(String.class),
                any(Duration.class));
    }

    @Test
    void redisFailureFallsBackToDatabase() {
        when(values.get("oma:test:moment:access:v1:1"))
                .thenThrow(new IllegalStateException("offline"));
        when(preferenceMapper.selectList(any())).thenReturn(List.of());
        when(chatMessageMapper.selectEstablishedDirectChatUserIds(1L)).thenReturn(List.of(5L));

        assertThat(service.accessFor(1L).relatedUserIds()).containsExactly(5L);
    }

    @Test
    void preferenceChangeEvictsBothUsers() {
        service.invalidatePreference(1L, 2L);

        verify(redis).delete(List.of(
                "oma:test:moment:access:v1:1",
                "oma:test:moment:access:v1:2"));
    }

    private MomentUserPreference preference(
            Long userId,
            Long targetUserId,
            boolean hideTarget,
            boolean hideMine
    ) {
        MomentUserPreference preference = new MomentUserPreference();
        preference.setUserId(userId);
        preference.setTargetUserId(targetUserId);
        preference.setHideTargetMoments(hideTarget);
        preference.setHideMyMoments(hideMine);
        return preference;
    }
}
