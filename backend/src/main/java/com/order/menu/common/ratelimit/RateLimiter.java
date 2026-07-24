package com.order.menu.common.ratelimit;

import java.time.Duration;

public interface RateLimiter {

    boolean tryAcquire(String action, String subject, int limit, Duration window);
}
