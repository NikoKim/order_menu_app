package com.order.menu.module.moment.service.cache;

import com.order.menu.module.moment.dto.MomentDtos;

import java.util.List;

public record TopicSearchCacheDto(
        List<MomentDtos.TopicResponse> records,
        long page,
        long pageSize,
        long total,
        boolean hasMore
) {
}
