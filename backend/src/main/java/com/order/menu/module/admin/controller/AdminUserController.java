package com.order.menu.module.admin.controller;

import com.order.menu.common.response.ApiResponse;
import com.order.menu.common.response.PageResponse;
import com.order.menu.common.util.PageUtils;
import com.order.menu.module.admin.dto.AdminDtos;
import com.order.menu.module.admin.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {
    private final AdminUserService adminUserService;

    @GetMapping
    public ApiResponse<PageResponse<AdminDtos.UserAccountResponse>> list(
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(adminUserService.list(role, PageUtils.page(page), PageUtils.pageSize(pageSize), keyword));
    }

    @PostMapping
    public ApiResponse<AdminDtos.UserAccountResponse> create(@Valid @RequestBody AdminDtos.CreateUserRequest request) {
        return ApiResponse.success(adminUserService.create(request));
    }

    @PutMapping("/{userId}/status")
    public ApiResponse<AdminDtos.UserAccountResponse> updateStatus(
            @PathVariable Long userId,
            @RequestBody AdminDtos.StatusRequest request) {
        return ApiResponse.success(adminUserService.updateStatus(userId, request));
    }
}
