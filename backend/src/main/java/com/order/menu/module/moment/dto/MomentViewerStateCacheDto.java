package com.order.menu.module.moment.dto;

import java.util.Set;

public record MomentViewerStateCacheDto(
        int schemaVersion,
        boolean liked,
        boolean read,
        Set<String> reactionEmojis
) {
    public MomentViewerStateCacheDto {
        schemaVersion = 1;
        reactionEmojis = reactionEmojis == null ? Set.of() : Set.copyOf(reactionEmojis);
    }

    public static MomentViewerStateCacheDto empty() {
        return new MomentViewerStateCacheDto(1, false, false, Set.of());
    }
}
