package com.order.menu.module.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class AdminDtos {
    private AdminDtos() {
    }

    public record UserAccountResponse(
            Long id,
            String nickname,
            String role,
            String phone,
            Integer status,
            LocalDateTime createdAt
    ) {
    }

    public record CreateUserRequest(
            @NotBlank(message = "昵称不能为空") @Size(max = 50, message = "昵称不能超过50个字符") String nickname,
            @NotBlank(message = "密码不能为空") @Size(min = 6, max = 72, message = "密码长度需要为6到72个字符") String password,
            @NotBlank(message = "角色不能为空") @Pattern(regexp = "CUSTOMER|SHOP", message = "只能创建顾客或商家账号") String role,
            @Size(max = 80, message = "店铺名不能超过80个字符") String shopName,
            @Size(max = 30, message = "手机号不能超过30个字符") String phone
    ) {
    }

    public record StatusRequest(Integer status) {
    }
}
