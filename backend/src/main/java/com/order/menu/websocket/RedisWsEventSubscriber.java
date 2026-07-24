package com.order.menu.websocket;

import com.order.menu.common.redis.RedisJsonCodec;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor
public class RedisWsEventSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisWsEventSubscriber.class);

    private final RedisJsonCodec codec;
    private final WsEventDispatcher dispatcher;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            dispatcher.dispatch(codec.read(json, WsEvent.class));
        } catch (RuntimeException exception) {
            log.warn("Ignoring malformed Redis WebSocket event", exception);
        }
    }
}
