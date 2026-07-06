package com.order.menu.module.user.controller;

import com.order.menu.common.response.ApiResponse;
import com.order.menu.module.user.dto.UserDtos;
import com.order.menu.module.user.service.UserService;
import com.order.menu.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserDtos.CurrentUserResponse> me() {
        return ApiResponse.success(userService.current(UserContext.get().userId()));
    }

    @PutMapping("/me")
    public ApiResponse<UserDtos.CurrentUserResponse> updateMe(@Valid @RequestBody UserDtos.UpdateMeRequest request) {
        return ApiResponse.success(userService.updateMe(UserContext.get().userId(), request));
    }
}
