package com.order.menu.module.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class FeedbackDtos {
    private FeedbackDtos() {
    }

    public record FeedbackRequest(Long shopId,
                                  @NotBlank(message = "留言内容不能为空") @Size(max = 1000, message = "留言不能超过1000个字符") String content) {
    }

    public record ReplyRequest(@NotBlank(message = "回复内容不能为空") @Size(max = 1000, message = "回复不能超过1000个字符") String reply) {
    }

    public record FeedbackResponse(Long id, Long userId, Long shopId, String content, String reply, String status, LocalDateTime createdAt) {
    }
}
