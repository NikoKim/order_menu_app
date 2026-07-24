package com.order.menu.module.moment.service;

import com.order.menu.common.redis.RedisFailurePolicy;
import com.order.menu.common.transaction.AfterCommitExecutor;
import com.order.menu.config.OrderMenuRedisProperties;
import com.order.menu.module.moment.mapper.MomentFeedExposureMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MomentFeedExposureServiceTest {

    private StringRedisTemplate redis;
    private ZSetOperations<String, String> zsets;
    private MomentFeedExposureMapper mapper;
    private MomentFeedExposureService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        zsets = mock(ZSetOperations.class);
        mapper = mock(MomentFeedExposureMapper.class);
        when(redis.opsForZSet()).thenReturn(zsets);
        OrderMenuRedisProperties properties = new OrderMenuRedisProperties();
        properties.setEnabled(true);
        properties.setMomentCacheEnabled(true);
        properties.setEnvironment("test");
        service = new MomentFeedExposureService(
                redis,
                new RedisFailurePolicy(new SimpleMeterRegistry()),
                new AfterCommitExecutor(),
                properties,
                mapper);
    }

    @Test
    void fullyResolvedCandidatesAvoidDatabase() {
        String exposedKey = "oma:test:moment:exposure:v1:1:RECOMMENDED";
        String checkedKey = "oma:test:moment:exposure-checked:v1:1:RECOMMENDED";
        when(zsets.range(exposedKey, 0, -1)).thenReturn(Set.of("10"));
        when(zsets.range(checkedKey, 0, -1)).thenReturn(Set.of("11"));

        Set<Long> result = service.findExposed(
                1L, "RECOMMENDED", List.of(10L, 11L));

        assertThat(result).containsExactly(10L);
        verifyNoInteractions(mapper);
    }

    @Test
    void unresolvedCandidatesAreVerifiedInDatabaseAndBackfilled() {
        String exposedKey = "oma:test:moment:exposure:v1:1:RECOMMENDED";
        String checkedKey = "oma:test:moment:exposure-checked:v1:1:RECOMMENDED";
        when(zsets.range(exposedKey, 0, -1)).thenReturn(Set.of("10"));
        when(zsets.range(checkedKey, 0, -1)).thenReturn(Set.of());
        when(mapper.selectExposedMomentIds(1L, "RECOMMENDED", List.of(11L, 12L)))
                .thenReturn(List.of(12L));

        Set<Long> result = service.findExposed(
                1L, "RECOMMENDED", List.of(10L, 11L, 12L));

        assertThat(result).containsExactlyInAnyOrder(10L, 12L);
        verify(zsets, times(2)).add(anyString(), anySet());
        verify(redis, times(2)).expire(anyString(), eq(Duration.ofDays(7)));
    }

    @Test
    void redisFailureFallsBackToCandidateScopedDatabaseQuery() {
        String exposedKey = "oma:test:moment:exposure:v1:1:RELATED";
        when(zsets.range(exposedKey, 0, -1))
                .thenThrow(new IllegalStateException("offline"));
        when(mapper.selectExposedMomentIds(1L, "RELATED", List.of(10L, 11L)))
                .thenReturn(List.of(11L));

        assertThat(service.findExposed(1L, "RELATED", List.of(10L, 11L)))
                .containsExactly(11L);
    }

    @Test
    void recordsDatabaseFirstThenMaintainsBoundedTtlZset() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 23, 12, 30);

        service.recordExposures(1L, "RECOMMENDED", List.of(10L, 10L, 11L), now);

        verify(mapper).insertIgnoreBatch(
                1L, "RECOMMENDED", List.of(10L, 11L), now);
        verify(zsets).remove(
                eq("oma:test:moment:exposure-checked:v1:1:RECOMMENDED"),
                any(Object[].class));
        verify(zsets).add(
                eq("oma:test:moment:exposure:v1:1:RECOMMENDED"),
                anySet());
        verify(zsets).removeRange(
                "oma:test:moment:exposure:v1:1:RECOMMENDED",
                0,
                -MomentFeedExposureService.MAX_CACHED_EXPOSURES - 1L);
        verify(redis).expire(
                "oma:test:moment:exposure:v1:1:RECOMMENDED",
                MomentFeedExposureService.CACHE_TTL);
    }
}
