package com.order.menu.module.moment.service.cache;

import java.time.Duration;

/**
 * Cross-instance mutex used only while rebuilding an expired moment cache entry.
 *
 * <p>{@link Lease.Status#BYPASS} means Redis is disabled or unavailable. The
 * caller must continue with its database fallback instead of failing the
 * request.</p>
 */
public interface MomentCacheRebuildGuard {

    Lease tryAcquire(String resource, String identity, Duration leaseTime);

    void release(Lease lease);

    record Lease(Status status, String lockKey, String token) {
        public enum Status {
            ACQUIRED,
            BUSY,
            BYPASS
        }

        public static Lease acquired(String lockKey, String token) {
            return new Lease(Status.ACQUIRED, lockKey, token);
        }

        public static Lease busy() {
            return new Lease(Status.BUSY, null, null);
        }

        public static Lease bypass() {
            return new Lease(Status.BYPASS, null, null);
        }

        public boolean acquired() {
            return status == Status.ACQUIRED;
        }

        public boolean shouldRebuild() {
            return status != Status.BUSY;
        }
    }
}
