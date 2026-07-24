package com.order.menu.common.redis;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
public class RedisFailurePolicy {

    private static final Logger log = LoggerFactory.getLogger(RedisFailurePolicy.class);
    private static final long FAILURE_LOG_INTERVAL_MILLIS = 30_000;

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, AtomicLong> lastFailureLog = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> degraded = new ConcurrentHashMap<>();

    public <T> T execute(String module, String operation, String key,
                         Supplier<T> action, T fallback) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            T value = action.get();
            meterRegistry.counter("order_menu.redis.operations", "module", module,
                    "operation", operation, "outcome", "success").increment();
            AtomicBoolean state = degraded.get(metricKey(module, operation));
            if (state != null && state.compareAndSet(true, false)) {
                log.info("Redis operation recovered, module={}, operation={}", module, operation);
            }
            return value;
        } catch (RuntimeException exception) {
            meterRegistry.counter("order_menu.redis.operations", "module", module,
                    "operation", operation, "outcome", "failure").increment();
            meterRegistry.counter("order_menu.redis.degraded", "module", module).increment();
            String metricKey = metricKey(module, operation);
            degraded.computeIfAbsent(metricKey, ignored -> new AtomicBoolean()).set(true);
            AtomicLong lastLogged = lastFailureLog.computeIfAbsent(metricKey, ignored -> new AtomicLong());
            long now = System.currentTimeMillis();
            long previous = lastLogged.get();
            if (now - previous >= FAILURE_LOG_INTERVAL_MILLIS
                    && lastLogged.compareAndSet(previous, now)) {
                log.warn("Redis operation failed, module={}, operation={}, key={}",
                        module, operation, key, exception);
            }
            return fallback;
        } finally {
            sample.stop(meterRegistry.timer("order_menu.redis.operation.duration",
                    "module", module, "operation", operation));
        }
    }

    public void execute(String module, String operation, String key, Runnable action) {
        execute(module, operation, key, () -> {
            action.run();
            return Boolean.TRUE;
        }, Boolean.FALSE);
    }

    public void recordCacheResult(String module, boolean hit) {
        meterRegistry.counter("order_menu.cache.requests", "module", module,
                "result", hit ? "hit" : "miss").increment();
        if (!hit) {
            meterRegistry.counter("order_menu.cache.database_fallback", "module", module).increment();
        }
    }

    private String metricKey(String module, String operation) {
        return module + ':' + operation;
    }
}
