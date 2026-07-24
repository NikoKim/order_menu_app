package com.order.menu.module.moment.service;

import com.order.menu.common.redis.RedisFailurePolicy;
import com.order.menu.common.redis.RedisJsonCodec;
import com.order.menu.common.redis.RedisKeyFactory;
import com.order.menu.common.transaction.AfterCommitExecutor;
import com.order.menu.config.OrderMenuRedisProperties;
import com.order.menu.module.moment.dto.MomentViewerStateCacheDto;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RedisMomentCacheAdapterTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private RedisJsonCodec codec;
    private RedisMomentCacheAdapter adapter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        codec = mock(RedisJsonCodec.class);
        when(redis.opsForValue()).thenReturn(values);
        OrderMenuRedisProperties properties = new OrderMenuRedisProperties();
        properties.setEnvironment("test");
        adapter = new RedisMomentCacheAdapter(redis, new RedisKeyFactory(properties),
                codec, properties, new AfterCommitExecutor(),
                new RedisFailurePolicy(new SimpleMeterRegistry()));
    }

    @Test
    void interactionEvictionRemovesEverySupportedFirstPageSize() {
        adapter.evictInteraction(8L);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Collection> keys = ArgumentCaptor.forClass(Collection.class);
        verify(redis, times(2)).delete(keys.capture());
        Collection<?> interactionKeys = keys.getAllValues().stream()
                .filter(value -> value.size() == 12)
                .findFirst().orElseThrow();
        assertThat(interactionKeys.stream().map(Object::toString).toList()).contains(
                "oma:test:moment:comments:first:v1:8:1",
                "oma:test:moment:comments:first:v1:8:10",
                "oma:test:moment:stats:v1:8",
                "oma:test:moment:hot-comment:v1:8");
    }

    @Test
    void redisFailureFallsBackToCacheMiss() {
        when(values.get("oma:test:moment:unread:v1:3")).thenThrow(new IllegalStateException("offline"));

        assertThat(adapter.getUnread(3L)).isEmpty();
    }

    @Test
    void viewerStatesUseOneMultiGetAndPipelineForBatchWrite() {
        List<String> keys=List.of(
                "oma:test:moment:viewer-state:v1:3:8",
                "oma:test:moment:viewer-state:v1:3:9");
        MomentViewerStateCacheDto first=new MomentViewerStateCacheDto(1,true,false,Set.of("HEART"));
        when(values.multiGet(keys)).thenReturn(java.util.Arrays.asList("first",null));
        when(codec.read("first",MomentViewerStateCacheDto.class)).thenReturn(first);

        assertThat(adapter.getViewerStates(3L,List.of(8L,9L)))
                .containsOnly(Map.entry(8L,first));
        adapter.putViewerStates(3L,Map.of(8L,first,9L,MomentViewerStateCacheDto.empty()));

        verify(values).multiGet(keys);
        verify(redis).executePipelined(any(org.springframework.data.redis.core.RedisCallback.class));
    }
}
