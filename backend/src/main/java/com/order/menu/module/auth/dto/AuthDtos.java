package com.order.menu.module.auth.dto;

import com.order.menu.module.user.dto.UserDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record CustomerRegisterRequest(
            @NotBlank(message = "昵称不能为空") @Size(max = 50, message = "昵称不能超过50个字符") String nickname,
            @NotBlank(message = "密码不能为空") @Size(min = 6, max = 72, message = "密码长度需为6到72个字符") String password
    ) {
    }

    public record LoginRequest(
            @NotBlank(message = "昵称不能为空") String nickname,
            @NotBlank(message = "密码不能为空") String password,
            @NotBlank(message = "角色不能为空") @Pattern(regexp = "CUSTOMER|SHOP|ADMIN", message = "角色非法") String role
    ) {
    }

    public record RegisterResponse(Long userId, String nickname) {
    }

    public record LoginResponse(String token, UserDtos.CurrentUserResponse user) {
    }
}
