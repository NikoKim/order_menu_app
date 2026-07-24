package com.order.menu.module.moment.dto;

import java.util.List;

public record MomentStatsCacheDto(
        long likeCount,
        long commentCount,
        long rootCommentCount,
        long shareCount,
        long appShareCount,
        long externalShareCount,
        List<MomentBaseCacheDto.ReactionSummary> reactions
) {
    public MomentStatsCacheDto {
        reactions = reactions == null ? List.of() : List.copyOf(reactions);
    }
}
