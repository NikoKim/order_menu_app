package com.order.menu.module.auth.controller;

import com.order.menu.common.response.ApiResponse;
import com.order.menu.module.auth.dto.AuthDtos;
import com.order.menu.module.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/customer/register")
    public ApiResponse<AuthDtos.RegisterResponse> register(@Valid @RequestBody AuthDtos.CustomerRegisterRequest request) {
        return ApiResponse.success(authService.registerCustomer(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }
}
