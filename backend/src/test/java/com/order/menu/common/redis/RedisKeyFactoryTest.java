package com.order.menu.common.redis;

import com.order.menu.config.OrderMenuRedisProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedisKeyFactoryTest {

    @Test
    void generatesVersionedRealtimeKeysWithEnvironmentPrefix() {
        OrderMenuRedisProperties properties = new OrderMenuRedisProperties();
        properties.setEnvironment("test");
        RedisKeyFactory factory = new RedisKeyFactory(properties);

        assertThat(factory.userOnline(7L)).isEqualTo("oma:test:presence:online:v1:7");
        assertThat(factory.conversationTyping(9L)).isEqualTo("oma:test:presence:typing:v1:9");
        assertThat(factory.rateLimit("order-create", "user-7"))
                .isEqualTo("oma:test:rate:order-create:v1:user-7");
    }
}
