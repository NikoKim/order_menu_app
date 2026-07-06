package com.order.menu.module.shop.controller;

import com.order.menu.common.response.ApiResponse;
import com.order.menu.common.response.PageResponse;
import com.order.menu.common.util.PageUtils;
import com.order.menu.module.shop.dto.ShopDtos;
import com.order.menu.module.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shops")
@RequiredArgsConstructor
public class ShopPublicController {
    private final ShopService shopService;

    @GetMapping
    public ApiResponse<PageResponse<ShopDtos.ShopResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String familyCode) {
        return ApiResponse.success(shopService.list(PageUtils.page(page), PageUtils.pageSize(pageSize), keyword, familyCode));
    }

    @GetMapping("/{shopId}")
    public ApiResponse<ShopDtos.ShopResponse> detail(@PathVariable Long shopId) {
        return ApiResponse.success(shopService.detail(shopId));
    }

    @GetMapping("/{shopId}/menu")
    public ApiResponse<ShopDtos.MenuResponse> menu(@PathVariable Long shopId) {
        return ApiResponse.success(shopService.menu(shopId));
    }
}
