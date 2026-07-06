package com.order.menu.module.dish.controller;

import com.order.menu.common.response.ApiResponse;
import com.order.menu.common.response.PageResponse;
import com.order.menu.common.util.PageUtils;
import com.order.menu.module.category.dto.CategoryDtos;
import com.order.menu.module.dish.dto.DishDtos;
import com.order.menu.module.dish.service.DishService;
import com.order.menu.security.UserContext;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shop")
@RequiredArgsConstructor
public class DishAdminController {
    private final DishService dishService;

    @GetMapping("/categories")
    public ApiResponse<List<CategoryDtos.CategoryResponse>> categories() {
        return ApiResponse.success(dishService.categories(UserContext.get().userId()));
    }

    @PostMapping("/categories")
    public ApiResponse<CategoryDtos.CategoryResponse> createCategory(@Valid @RequestBody CategoryDtos.CategoryRequest request) {
        return ApiResponse.success(dishService.createCategory(UserContext.get().userId(), request));
    }

    @PutMapping("/categories/{categoryId}")
    public ApiResponse<CategoryDtos.CategoryResponse> updateCategory(@PathVariable Long categoryId, @Valid @RequestBody CategoryDtos.CategoryRequest request) {
        return ApiResponse.success(dishService.updateCategory(UserContext.get().userId(), categoryId, request));
    }

    @DeleteMapping("/categories/{categoryId}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long categoryId) {
        dishService.deleteCategory(UserContext.get().userId(), categoryId);
        return ApiResponse.success();
    }

    @GetMapping("/dishes")
    public ApiResponse<PageResponse<DishDtos.DishResponse>> dishes(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(dishService.shopDishes(UserContext.get().userId(), PageUtils.page(page), PageUtils.pageSize(pageSize), categoryId, keyword, status));
    }

    @PostMapping("/dishes")
    public ApiResponse<DishDtos.DishResponse> createDish(@Valid @RequestBody DishDtos.DishRequest request) {
        return ApiResponse.success(dishService.createDish(UserContext.get().userId(), request));
    }

    @PutMapping("/dishes/{dishId}")
    public ApiResponse<DishDtos.DishResponse> updateDish(@PathVariable Long dishId, @Valid @RequestBody DishDtos.DishRequest request) {
        return ApiResponse.success(dishService.updateDish(UserContext.get().userId(), dishId, request));
    }

    @PutMapping("/dishes/{dishId}/status")
    public ApiResponse<DishDtos.DishResponse> updateStatus(@PathVariable Long dishId, @Valid @RequestBody DishDtos.DishStatusRequest request) {
        return ApiResponse.success(dishService.updateStatus(UserContext.get().userId(), dishId, request.status()));
    }

    @DeleteMapping("/dishes/{dishId}")
    public ApiResponse<Void> deleteDish(@PathVariable Long dishId) {
        dishService.deleteDish(UserContext.get().userId(), dishId);
        return ApiResponse.success();
    }
}
