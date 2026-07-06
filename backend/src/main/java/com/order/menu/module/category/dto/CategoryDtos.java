package com.order.menu.module.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CategoryDtos {
    private CategoryDtos() {
    }

    public record CategoryResponse(Long id, String name, Integer sortOrder, Integer status) {
    }

    public record CategoryRequest(
            @NotBlank(message = "分类名称不能为空") @Size(max = 50, message = "分类名称不能超过50个字符") String name,
            Integer sortOrder
    ) {
    }
}
