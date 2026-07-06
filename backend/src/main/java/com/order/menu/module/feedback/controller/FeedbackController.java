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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {
    private final FeedbackService feedbackService;

    @PostMapping
    public ApiResponse<FeedbackDtos.FeedbackResponse> create(@Valid @RequestBody FeedbackDtos.FeedbackRequest request) {
        return ApiResponse.success(feedbackService.create(UserContext.get().userId(), request));
    }

    @GetMapping("/my")
    public ApiResponse<PageResponse<FeedbackDtos.FeedbackResponse>> my(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(feedbackService.my(UserContext.get().userId(), PageUtils.page(page), PageUtils.pageSize(pageSize)));
    }
}
