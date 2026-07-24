package com.order.menu.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "order-menu.redis.realtime")
public class RedisRealtimeProperties {

    private boolean presenceEnabled;
    private boolean rateLimitEnabled;

    private long onlineTtlSeconds = 90;
    private long typingTtlSeconds = 8;

    private int loginLimit = 10;
    private long loginWindowSeconds = 60;
    private int loginAccountLimit = 20;
    private long loginAccountWindowSeconds = 300;
    private int orderCreateLimit = 5;
    private long orderCreateWindowSeconds = 60;
    private int messageSendLimit = 60;
    private long messageSendWindowSeconds = 60;
    private int momentCommentLimit = 30;
    private long momentCommentWindowSeconds = 60;
}
