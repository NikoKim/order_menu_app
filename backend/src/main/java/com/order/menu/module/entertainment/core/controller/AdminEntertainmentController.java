package com.order.menu.module.entertainment.core.controller;

import com.order.menu.common.response.ApiResponse;
import com.order.menu.common.response.PageResponse;
import com.order.menu.module.entertainment.core.dto.EntertainmentDtos;
import com.order.menu.module.entertainment.core.service.EntertainmentGameService;
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
@RequestMapping("/api/admin/entertainment")
@RequiredArgsConstructor
public class AdminEntertainmentController {
    private final EntertainmentGameService gameService;

    @GetMapping("/games")
    public ApiResponse<PageResponse<EntertainmentDtos.GameSummaryResponse>> games(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(gameService.adminList(page, pageSize, status, keyword));
    }

    @PutMapping("/games/{gameId}/status")
    public ApiResponse<Void> changeStatus(@PathVariable Long gameId,
                                           @Valid @RequestBody EntertainmentController.StatusRequest request) {
        gameService.adminChangeStatus(gameId, request.status());
        return ApiResponse.success();
    }
}
