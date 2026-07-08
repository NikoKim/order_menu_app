package com.order.menu.module.shop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class ShopDtos {
    private ShopDtos() {
    }

    public record ShopResponse(Long id, String name, String description, String coverImageUrl, String address,
                               String phone, String businessStatus, String visibilityType, String familyName,
                               String familyCode, String familyJoinMode, String familyImageUrl,
                               List<com.order.menu.module.dish.dto.DishDtos.DishResponse> previewDishes) {
    }

    public record ShopUpdateRequest(
            @NotBlank(message = "店铺名称不能为空") @Size(max = 80, message = "店铺名称不能超过80个字符") String name,
            @Size(max = 500, message = "店铺描述不能超过500个字符") String description,
            @Size(max = 255, message = "图片地址不能超过255个字符") String coverImageUrl,
            @Size(max = 255, message = "地址不能超过255个字符") String address,
            @Size(max = 30, message = "电话不能超过30个字符") String phone,
            String businessStatus,
            String visibilityType,
            @Size(max = 80, message = "家庭名称不能超过80个字符") String familyName,
            String familyJoinMode,
            @Size(max = 255, message = "家庭图片地址不能超过255个字符") String familyImageUrl
    ) {
    }

    public record MenuResponse(ShopResponse shop, List<CategoryMenuResponse> categories) {
    }

    public record CategoryMenuResponse(Long id, String name, List<com.order.menu.module.dish.dto.DishDtos.DishResponse> dishes) {
    }
}
