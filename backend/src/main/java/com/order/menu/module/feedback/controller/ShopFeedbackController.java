package com.order.menu.module.feedback.controller;

import com.order.menu.common.response.ApiResponse;
import com.order.menu.common.response.PageResponse;
import com.order.menu.common.util.PageUtils;
import com.order.menu.module.feedback.dto.FeedbackDtos;
import com.order.menu.module.feedback.service.FeedbackService;
import com.order.menu.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shop/feedback")
@RequiredArgsConstructor
public class ShopFeedbackController {
    private final FeedbackService feedbackService;

    @GetMapping
    public ApiResponse<PageResponse<FeedbackDtos.FeedbackResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(feedbackService.shopFeedback(UserContext.get().userId(), PageUtils.page(page), PageUtils.pageSize(pageSize), status));
    }

    @PutMapping("/{feedbackId}/reply")
    public ApiResponse<FeedbackDtos.FeedbackResponse> reply(@PathVariable Long feedbackId, @Valid @RequestBody FeedbackDtos.ReplyRequest request) {
        return ApiResponse.success(feedbackService.reply(UserContext.get().userId(), feedbackId, request));
    }
}
