package com.order.menu.module.message.service.presence;

import com.order.menu.common.redis.RedisKeyFactory;
import com.order.menu.common.redis.RedisFailurePolicy;
import com.order.menu.config.RedisRealtimeProperties;
import com.order.menu.module.message.mapper.ConversationMemberMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties(RedisRealtimeProperties.class)
public class ChatPresenceConfiguration {

    @Bean
    @ConditionalOnExpression("'${order-menu.redis.enabled:false}' == 'true' && "
            + "'${order-menu.redis.realtime.presence-enabled:false}' == 'true'")
    ChatPresenceService redisChatPresenceService(
            StringRedisTemplate redisTemplate,
            RedisKeyFactory keyFactory,
            RedisRealtimeProperties properties,
            ConversationMemberMapper conversationMemberMapper,
            RedisFailurePolicy failurePolicy) {
        return new RedisChatPresenceService(
                redisTemplate, keyFactory, properties, conversationMemberMapper, failurePolicy);
    }

    @Bean
    @ConditionalOnExpression("'${order-menu.redis.enabled:false}' != 'true' || "
            + "'${order-menu.redis.realtime.presence-enabled:false}' != 'true'")
    ChatPresenceService noopChatPresenceService() {
        return new NoopChatPresenceService();
    }
}
