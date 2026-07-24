package com.order.menu.module.moment.service;

import java.util.Set;

public record MomentAccessSnapshot(
        Set<Long> blockedAuthorIds,
        Set<Long> relatedUserIds
) {
    public MomentAccessSnapshot {
        blockedAuthorIds = blockedAuthorIds == null ? Set.of() : Set.copyOf(blockedAuthorIds);
        relatedUserIds = relatedUserIds == null ? Set.of() : Set.copyOf(relatedUserIds);
    }
}
