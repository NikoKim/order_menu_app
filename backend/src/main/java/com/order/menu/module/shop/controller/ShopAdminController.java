package com.order.menu.module.shop.controller;

import com.order.menu.common.response.ApiResponse;
import com.order.menu.module.shop.dto.ShopDtos;
import com.order.menu.module.shop.service.ShopService;
import com.order.menu.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shop")
@RequiredArgsConstructor
public class ShopAdminController {
    private final ShopService shopService;

    @GetMapping("/me")
    public ApiResponse<ShopDtos.ShopResponse> me() {
        return ApiResponse.success(shopService.myShop(UserContext.get().userId()));
    }

    @PutMapping("/me")
    public ApiResponse<ShopDtos.ShopResponse> update(@Valid @RequestBody ShopDtos.ShopUpdateRequest request) {
        return ApiResponse.success(shopService.updateMyShop(UserContext.get().userId(), request));
    }
}
