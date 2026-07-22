package com.order.menu.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OrderMenuRedisProperties.class)
public class RedisInfrastructureConfiguration {
}
