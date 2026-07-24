package com.order.menu.module.moment.service.cache;

import java.time.Duration;

public class NoopMomentCacheRebuildGuard implements MomentCacheRebuildGuard {

    @Override
    public Lease tryAcquire(String resource, String identity, Duration leaseTime) {
        return Lease.bypass();
    }

    @Override
    public void release(Lease lease) {
    }
}
