package com.order.menu.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "order-menu.redis")
public class OrderMenuRedisProperties {

    private boolean enabled;
    private String environment = "dev";

    private boolean menuCacheEnabled;
    private boolean momentCacheEnabled;
    private boolean chatCacheEnabled;
    private boolean unreadCacheEnabled;
    private boolean idempotencyEnabled;
    private boolean websocketBusEnabled;

    private long shopDetailTtlSeconds = 300;
    private long shopMenuTtlSeconds = 120;
    private long momentBaseTtlSeconds = 300;
    private long momentListTtlSeconds = 120;
    private long momentUnreadTtlSeconds = 60;
    private long momentTopicTtlSeconds = 300;
    private long momentCommentTtlSeconds = 120;
    private long momentStatsTtlSeconds = 120;
    private long momentViewerStateTtlSeconds = 180;
    private long chatMessageTtlSeconds = 1800;
    private long chatRecentTtlSeconds = 1800;
    private long unreadCountTtlSeconds = 300;
    private long idempotencyPendingTtlSeconds = 120;
    private long idempotencySuccessTtlSeconds = 600;
}
