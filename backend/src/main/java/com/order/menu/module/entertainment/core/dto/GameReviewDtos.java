package com.order.menu.module.entertainment.core.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class GameReviewDtos {
    private GameReviewDtos() {}

    public record SaveRequest(@NotNull @Min(1) @Max(5) Integer rating,
                              @NotBlank @Size(max = 1000) String content) {}

    public record ReviewResponse(Long id, Long gameId, EntertainmentDtos.CreatorResponse author,
                                 int rating, String content, LocalDateTime createdAt,
                                 LocalDateTime updatedAt, boolean mine) {}

    public record ReviewPageResponse(List<ReviewResponse> records, long page, long pageSize, long total,
                                     BigDecimal averageRating, boolean canReview,
                                     ReviewResponse myReview) {}
}
