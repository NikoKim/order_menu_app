package com.order.menu.common.redis;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedisFailurePolicyTest {

    @Test
    void recordsFailuresAndReturnsFallbackWithoutEscapingTheRedisException() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RedisFailurePolicy policy = new RedisFailurePolicy(registry);

        String result = policy.execute("menu", "read", "test-key", () -> {
            throw new IllegalStateException("redis down");
        }, "database");

        assertThat(result).isEqualTo("database");
        assertThat(registry.get("order_menu.redis.operations")
                .tags("module", "menu", "operation", "read", "outcome", "failure")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("order_menu.redis.degraded")
                .tag("module", "menu").counter().count()).isEqualTo(1.0);
    }

    @Test
    void recordsCacheHitsAndDatabaseFallbacks() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RedisFailurePolicy policy = new RedisFailurePolicy(registry);

        policy.recordCacheResult("moment", true);
        policy.recordCacheResult("moment", false);

        assertThat(registry.get("order_menu.cache.requests")
                .tags("module", "moment", "result", "hit")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("order_menu.cache.database_fallback")
                .tag("module", "moment").counter().count()).isEqualTo(1.0);
    }
}
