package com.order.menu.module.moment.dto;

import java.util.List;

public record MomentIdPageCacheDto(List<Long> ids, long page, long pageSize, long total, boolean hasMore) {
    public MomentIdPageCacheDto {
        ids = ids == null ? List.of() : List.copyOf(ids);
    }
}
