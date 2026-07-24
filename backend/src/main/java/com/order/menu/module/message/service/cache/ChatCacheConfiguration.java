package com.order.menu.module.message.service.cache;

import com.order.menu.common.redis.RedisJsonCodec;
import com.order.menu.common.redis.RedisKeyFactory;
import com.order.menu.common.redis.RedisFailurePolicy;
import com.order.menu.config.OrderMenuRedisProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class ChatCacheConfiguration {

    @Bean
    @ConditionalOnExpression(
            "'${order-menu.redis.enabled:false}' == 'true' "
            + "and '${order-menu.redis.chat-cache-enabled:false}' == 'true'"
    )
    public ChatCachePort redisChatCache(
            StringRedisTemplate redisTemplate,
            RedisKeyFactory keyFactory,
            RedisJsonCodec codec,
            OrderMenuRedisProperties properties,
            RedisFailurePolicy failurePolicy
    ) {
        return new RedisChatCache(redisTemplate, keyFactory, codec, properties, failurePolicy);
    }

    @Bean
    @ConditionalOnMissingBean(ChatCachePort.class)
    public ChatCachePort noopChatCache() {
        return new NoopChatCache();
    }
}
