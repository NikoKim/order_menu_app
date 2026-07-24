package com.order.menu.websocket;

import com.order.menu.common.redis.RedisJsonCodec;
import com.order.menu.common.redis.RedisKeyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisWsEventTransportTest {

    private StringRedisTemplate redisTemplate;
    private WsEventDispatcher dispatcher;
    private RedisWsEventTransport transport;
    private WsEvent event;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        RedisKeyFactory keyFactory = mock(RedisKeyFactory.class);
        RedisJsonCodec codec = mock(RedisJsonCodec.class);
        dispatcher = mock(WsEventDispatcher.class);
        transport = new RedisWsEventTransport(redisTemplate, keyFactory, codec, dispatcher);
        event = WsEvent.create(WsTargetType.USER, 7L,
                WsMessage.of("CHAT", "UPDATED", java.util.Map.of()), "instance-a");
        when(keyFactory.websocketChannel()).thenReturn("oma:test:ws:events");
        when(codec.write(event)).thenReturn("json");
    }

    @Test
    void redisDeliveryDoesNotAlsoPushLocally() {
        when(redisTemplate.convertAndSend("oma:test:ws:events", "json")).thenReturn(2L);

        transport.publish(event);

        verify(dispatcher, never()).dispatch(event);
    }

    @Test
    void noSubscribersFallsBackToLocalDelivery() {
        when(redisTemplate.convertAndSend("oma:test:ws:events", "json")).thenReturn(0L);

        transport.publish(event);

        verify(dispatcher).dispatch(event);
    }

    @Test
    void redisFailureFallsBackToLocalDelivery() {
        when(redisTemplate.convertAndSend("oma:test:ws:events", "json"))
                .thenThrow(new IllegalStateException("redis unavailable"));

        transport.publish(event);

        verify(dispatcher).dispatch(event);
    }
}
