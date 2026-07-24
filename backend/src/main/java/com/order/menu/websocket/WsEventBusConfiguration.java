package com.order.menu.websocket;

import com.order.menu.common.redis.RedisJsonCodec;
import com.order.menu.common.redis.RedisKeyFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.UUID;

@Configuration
public class WsEventBusConfiguration {

    private static final String BUS_ENABLED =
            "'${order-menu.redis.enabled:false}' == 'true' "
                    + "and '${order-menu.redis.websocket-bus-enabled:false}' == 'true'";

    @Bean
    public WsInstanceIdentity wsInstanceIdentity() {
        return new WsInstanceIdentity(UUID.randomUUID().toString());
    }

    @Bean
    @ConditionalOnExpression(BUS_ENABLED)
    public RedisWsEventSubscriber redisWsEventSubscriber(RedisJsonCodec codec,
                                                         WsEventDispatcher dispatcher) {
        return new RedisWsEventSubscriber(codec, dispatcher);
    }

    @Bean
    @ConditionalOnExpression(BUS_ENABLED)
    public RedisMessageListenerContainer wsRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisWsEventSubscriber subscriber,
            RedisKeyFactory keyFactory
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(keyFactory.websocketChannel()));
        return container;
    }

    @Bean
    @ConditionalOnExpression(BUS_ENABLED)
    public WsEventTransport redisWsEventTransport(StringRedisTemplate redisTemplate,
                                                  RedisKeyFactory keyFactory,
                                                  RedisJsonCodec codec,
                                                  WsEventDispatcher dispatcher) {
        return new RedisWsEventTransport(redisTemplate, keyFactory, codec, dispatcher);
    }

    @Bean
    @ConditionalOnMissingBean(WsEventTransport.class)
    public WsEventTransport localWsEventTransport(WsEventDispatcher dispatcher) {
        return new LocalWsEventTransport(dispatcher);
    }
}
