package com.order.menu.module.user.dto;

import jakarta.validation.constraints.Size;

public final class UserDtos {
    private UserDtos() {
    }

    public record UpdateMeRequest(
            @Size(max = 50, message = "昵称不能超过50个字符") String nickname,
            @Size(max = 30, message = "手机号不能超过30个字符") String phone,
            @Size(max = 255, message = "头像地址不能超过255个字符") String avatarUrl
    ) {
    }

    public record CurrentUserResponse(Long id, String nickname, String role, String avatarUrl, String phone) {
    }
}
