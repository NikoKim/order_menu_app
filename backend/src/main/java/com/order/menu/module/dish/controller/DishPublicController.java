package com.order.menu.module.dish.controller;

import com.order.menu.common.response.ApiResponse;
import com.order.menu.module.dish.dto.DishDtos;
import com.order.menu.module.dish.service.DishService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dishes")
@RequiredArgsConstructor
public class DishPublicController {
    private final DishService dishService;

    @GetMapping("/{dishId}")
    public ApiResponse<DishDtos.DishResponse> detail(@PathVariable Long dishId) {
        return ApiResponse.success(dishService.detail(dishId));
    }
}
