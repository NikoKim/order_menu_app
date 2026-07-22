package com.order.menu;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=6379",
        "spring.data.redis.password=1714672038qwe",
        "spring.data.redis.database=0",
        "order-menu.redis.enabled=true"
})
class RedisConnectionTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void shouldReadAndWriteRedis() {
        String key = "oma:dev:test:junit";

        redisTemplate.opsForValue().set(key, "ok");
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("ok");
        redisTemplate.delete(key);
    }
}
