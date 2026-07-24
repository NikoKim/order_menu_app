package com.order.menu.module.moment.service.cache;

import com.order.menu.common.redis.RedisFailurePolicy;
import com.order.menu.common.redis.RedisJsonCodec;
import com.order.menu.common.transaction.AfterCommitExecutor;
import com.order.menu.config.OrderMenuRedisProperties;
import com.order.menu.module.moment.dto.MomentDtos;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisMomentAuxiliaryCacheTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private RedisJsonCodec codec;
    private RedisMomentAuxiliaryCache cache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        codec = mock(RedisJsonCodec.class);
        when(redis.opsForValue()).thenReturn(values);
        OrderMenuRedisProperties properties = new OrderMenuRedisProperties();
        properties.setEnvironment("test");
        cache = new RedisMomentAuxiliaryCache(
                redis,
                codec,
                new RedisFailurePolicy(new SimpleMeterRegistry()),
                new AfterCommitExecutor(),
                properties
        );
    }

    @Test
    void commentLikesUseOneMultiGetAndPreserveCachedFalse() {
        when(values.multiGet(List.of(
                "oma:test:moment:aux:comment-like:v1:7:10",
                "oma:test:moment:aux:comment-like:v1:7:11"
        ))).thenReturn(List.of("1", "0"));

        Map<Long, Boolean> result = cache.getCommentLikes(7L, List.of(10L, 11L, 10L));

        assertThat(result).containsEntry(10L, true).containsEntry(11L, false);
        verify(values).multiGet(List.of(
                "oma:test:moment:aux:comment-like:v1:7:10",
                "oma:test:moment:aux:comment-like:v1:7:11"
        ));
    }

    @Test
    void partialCommentLikeHitReturnsOnlyKnownStatesForDatabaseFallback() {
        when(values.multiGet(any())).thenReturn(java.util.Arrays.asList("1", null));

        Map<Long, Boolean> result = cache.getCommentLikes(7L, List.of(10L, 11L));

        assertThat(result).containsOnly(Map.entry(10L, true));
    }

    @Test
    void commentLikeWriteCachesBothPositiveAndNegativeStatesWithShortTtl() {
        cache.putCommentLikes(7L, Map.of(10L, true, 11L, false));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(values, org.mockito.Mockito.times(2)).set(key.capture(), value.capture(), ttl.capture());
        assertThat(key.getAllValues()).containsExactlyInAnyOrder(
                "oma:test:moment:aux:comment-like:v1:7:10",
                "oma:test:moment:aux:comment-like:v1:7:11"
        );
        assertThat(value.getAllValues()).containsExactlyInAnyOrder("1", "0");
        assertThat(ttl.getAllValues()).allSatisfy(duration ->
                assertThat(duration.getSeconds()).isBetween(45L, 55L));
    }

    @Test
    void redisFailureIsACommentStateMiss() {
        when(values.multiGet(any())).thenThrow(new IllegalStateException("offline"));

        assertThat(cache.getCommentLikes(7L, List.of(10L))).isEmpty();
    }

    @Test
    void missingMarkerIsShortLivedAndRedisFailureDoesNotClaimMissing() {
        cache.markMomentMissing(99L);

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(values).set(eq("oma:test:moment:aux:missing-moment:v1:99"), eq("1"), ttl.capture());
        assertThat(ttl.getValue().getSeconds()).isBetween(15L, 25L);

        when(redis.hasKey("oma:test:moment:aux:missing-comment:v1:42"))
                .thenThrow(new IllegalStateException("offline"));
        assertThat(cache.isCommentMissing(42L)).isFalse();
    }

    @Test
    void mentionSearchHashesQueryAndCachesOnlyPublicAuthorShape() {
        MomentDtos.AuthorResponse author =
                new MomentDtos.AuthorResponse(3L, "Ada", "/avatar.png", "USER", false);
        when(codec.write(List.of(author))).thenReturn("authors-json");

        cache.putMentionSearch("Private Search Text", List.of(author));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(values).set(key.capture(), eq("authors-json"), any(Duration.class));
        assertThat(key.getValue())
                .startsWith("oma:test:moment:aux:mention-search:v1:0:")
                .doesNotContain("Private", "Search", "Text");
    }

    @Test
    void invalidAuthorIdIsNotCached() {
        MomentDtos.AuthorResponse author =
                new MomentDtos.AuthorResponse(4L, "Ada", "/avatar.png", "USER", false);

        cache.putAuthor(3L, author);

        verify(codec, never()).write(any());
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }
}
