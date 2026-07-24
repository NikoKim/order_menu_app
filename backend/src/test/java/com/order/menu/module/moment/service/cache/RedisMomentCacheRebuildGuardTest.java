package com.order.menu.module.moment.service.cache;

import com.order.menu.common.redis.RedisFailurePolicy;
import com.order.menu.config.OrderMenuRedisProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisMomentCacheRebuildGuardTest {

    private static final String EXPECTED_KEY =
            "oma:test:moment:rebuild-lock:v1:hot-moments:"
                    + "6ca13d52ca70c883e0f0bb10";

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private RedisMomentCacheRebuildGuard guard;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        OrderMenuRedisProperties properties = new OrderMenuRedisProperties();
        properties.setEnvironment("test");
        guard = new RedisMomentCacheRebuildGuard(
                redis,
                new RedisFailurePolicy(new SimpleMeterRegistry()),
                properties
        );
    }

    @Test
    void acquireUsesSetNxWithShortLeaseAndOpaqueToken() {
        when(values.setIfAbsent(eq(EXPECTED_KEY), any(), eq(Duration.ofSeconds(8))))
                .thenReturn(true);

        MomentCacheRebuildGuard.Lease lease =
                guard.tryAcquire("hot-moments", "abc123", Duration.ofSeconds(8));

        assertThat(lease.status()).isEqualTo(MomentCacheRebuildGuard.Lease.Status.ACQUIRED);
        assertThat(lease.lockKey()).isEqualTo(EXPECTED_KEY);
        assertThat(lease.token()).isNotBlank().doesNotContain("abc123");
        assertThat(lease.shouldRebuild()).isTrue();
    }

    @Test
    void occupiedLockReturnsBusyWithoutPretendingRedisFailed() {
        when(values.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(false);

        MomentCacheRebuildGuard.Lease lease =
                guard.tryAcquire("hot-topics", "global", Duration.ofSeconds(5));

        assertThat(lease.status()).isEqualTo(MomentCacheRebuildGuard.Lease.Status.BUSY);
        assertThat(lease.shouldRebuild()).isFalse();
    }

    @Test
    void redisFailureReturnsBypassSoDatabaseFallbackRemainsAvailable() {
        when(values.setIfAbsent(any(), any(), any(Duration.class)))
                .thenThrow(new IllegalStateException("offline"));

        MomentCacheRebuildGuard.Lease lease =
                guard.tryAcquire("hot-topics", "global", Duration.ofSeconds(5));

        assertThat(lease.status()).isEqualTo(MomentCacheRebuildGuard.Lease.Status.BYPASS);
        assertThat(lease.shouldRebuild()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void releaseUsesTokenCheckedLuaInsteadOfPlainDelete() {
        when(values.setIfAbsent(eq(EXPECTED_KEY), any(), eq(Duration.ofSeconds(8))))
                .thenReturn(true);
        MomentCacheRebuildGuard.Lease lease =
                guard.tryAcquire("hot-moments", "abc123", Duration.ofSeconds(8));

        guard.release(lease);

        ArgumentCaptor<RedisScript<Long>> script = ArgumentCaptor.forClass(RedisScript.class);
        verify(redis).execute(script.capture(), eq(List.of(EXPECTED_KEY)), eq(lease.token()));
        assertThat(script.getValue().getScriptAsString())
                .contains("redis.call('GET', KEYS[1]) == ARGV[1]")
                .contains("redis.call('DEL', KEYS[1])");
        verify(redis, never()).delete(EXPECTED_KEY);
    }

    @Test
    void releaseIgnoresBusyAndBypassLeases() {
        guard.release(MomentCacheRebuildGuard.Lease.busy());
        guard.release(MomentCacheRebuildGuard.Lease.bypass());

        verify(redis, never()).execute(any(RedisScript.class), any(List.class), any());
    }

    @Test
    void rejectsLongLeasesAndUnsafeResourceNames() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                guard.tryAcquire("hot moments", "global", Duration.ofSeconds(5)));
        assertThatIllegalArgumentException().isThrownBy(() ->
                guard.tryAcquire("hot-moments", "global", Duration.ofSeconds(31)));
    }
}
