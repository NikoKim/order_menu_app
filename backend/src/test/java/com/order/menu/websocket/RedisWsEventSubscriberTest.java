package com.order.menu.websocket;

import com.order.menu.common.redis.RedisJsonCodec;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisWsEventSubscriberTest {

    @Test
    void dispatchesDecodedEvent() {
        RedisJsonCodec codec = mock(RedisJsonCodec.class);
        WsEventDispatcher dispatcher = mock(WsEventDispatcher.class);
        Message message = mock(Message.class);
        WsEvent event = WsEvent.create(WsTargetType.SHOP, 3L,
                WsMessage.of("ORDER", "CREATED", Map.of()), "instance-b");
        when(message.getBody()).thenReturn("json".getBytes(StandardCharsets.UTF_8));
        when(codec.read("json", WsEvent.class)).thenReturn(event);

        new RedisWsEventSubscriber(codec, dispatcher).onMessage(message, null);

        verify(dispatcher).dispatch(event);
    }

    @Test
    void ignoresMalformedEvent() {
        RedisJsonCodec codec = mock(RedisJsonCodec.class);
        WsEventDispatcher dispatcher = mock(WsEventDispatcher.class);
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn("bad".getBytes(StandardCharsets.UTF_8));
        when(codec.read("bad", WsEvent.class)).thenThrow(new IllegalStateException("bad json"));

        new RedisWsEventSubscriber(codec, dispatcher).onMessage(message, null);

        verify(dispatcher, never()).dispatch(org.mockito.ArgumentMatchers.any());
    }
}
