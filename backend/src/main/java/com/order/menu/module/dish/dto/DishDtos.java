package com.order.menu.module.dish.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public final class DishDtos {
    private DishDtos() {
    }

    public record DishResponse(Long id, Long shopId, Long categoryId, String name, String description,
                               BigDecimal price, String imageUrl, Integer stock, Integer salesCount,
                               String status, Integer sortOrder, Integer recommendOrder, // 新增
                               List<OptionGroupResponse> optionGroups) {
    }

    public record OptionGroupResponse(Long id, String name, Boolean required, String selectionType,
                                      Integer minSelect, Integer maxSelect, Integer sortOrder,
                                      List<OptionItemResponse> options) {
    }

    public record OptionItemResponse(Long id, String name, BigDecimal priceDelta, Integer sortOrder) {
    }

    public record DishRequest(
            Long categoryId,
            @NotBlank(message = "菜品名称不能为空") @Size(max = 80, message = "菜品名称不能超过80个字符") String name,
            @Size(max = 500, message = "菜品描述不能超过500个字符") String description,
            @NotNull(message = "价格不能为空") @DecimalMin(value = "0.0", message = "价格不能为负") BigDecimal price,
            @Size(max = 255, message = "图片地址不能超过255个字符") String imageUrl,
            @Min(value = 0, message = "Stock cannot be negative") Integer stock,
            String status,
            Integer sortOrder,
            List<OptionGroupRequest> optionGroups
    ) {
    }

    public record OptionGroupRequest(
            Long id,
            @NotBlank(message = "属性名称不能为空") @Size(max = 50, message = "属性名称不能超过50个字符") String name,
            Boolean required,
            String selectionType,
            Integer minSelect,
            Integer maxSelect,
            Integer sortOrder,
            List<OptionItemRequest> options
    ) {
    }

    public record OptionItemRequest(
            Long id,
            @NotBlank(message = "选项名称不能为空") @Size(max = 50, message = "选项名称不能超过50个字符") String name,
            @NotNull(message = "加价不能为空") @DecimalMin(value = "0.0", message = "加价不能为负") BigDecimal priceDelta,
            Integer sortOrder
    ) {
    }

    public record DishStatusRequest(@NotBlank(message = "状态不能为空") String status) {
    }
}
