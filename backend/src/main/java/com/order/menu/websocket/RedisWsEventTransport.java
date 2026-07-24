package com.order.menu.websocket;

import com.order.menu.common.redis.RedisJsonCodec;
import com.order.menu.common.redis.RedisKeyFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@RequiredArgsConstructor
public class RedisWsEventTransport implements WsEventTransport {

    private static final Logger log = LoggerFactory.getLogger(RedisWsEventTransport.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory keyFactory;
    private final RedisJsonCodec codec;
    private final WsEventDispatcher localDispatcher;

    @Override
    public void publish(WsEvent event) {
        try {
            Long subscribers = redisTemplate.convertAndSend(keyFactory.websocketChannel(), codec.write(event));
            if (subscribers == null || subscribers == 0) {
                log.warn("Redis WebSocket channel has no subscribers; delivering event locally, eventId={}",
                        event.eventId());
                localDispatcher.dispatch(event);
            }
        } catch (RuntimeException exception) {
            log.warn("Redis WebSocket publish failed; delivering event locally, eventId={}",
                    event.eventId(), exception);
            localDispatcher.dispatch(event);
        }
    }
}
