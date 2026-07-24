package com.order.menu.module.dish.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.order.menu.common.constant.StatusConstants;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.common.response.PageResponse;
import com.order.menu.module.category.dto.CategoryDtos;
import com.order.menu.module.category.entity.MenuCategory;
import com.order.menu.module.category.mapper.MenuCategoryMapper;
import com.order.menu.module.dish.dto.DishDtos;
import com.order.menu.module.dish.entity.Dish;
import com.order.menu.module.dish.mapper.DishMapper;
import com.order.menu.module.shop.entity.Shop;
import com.order.menu.module.shop.service.ShopService;
import com.order.menu.module.shop.service.cache.MenuCacheInvalidator;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DishService {
    private final DishMapper dishMapper;
    private final MenuCategoryMapper categoryMapper;
    private final ShopService shopService;
    private final DishOptionService dishOptionService;
    private final MenuCacheInvalidator menuCacheInvalidator;

    public DishDtos.DishResponse detail(Long dishId) {
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null || !StatusConstants.DISH_ON_SALE.equals(dish.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Dish not found or off sale");
        }
        return toResponse(dish);
    }

    public List<CategoryDtos.CategoryResponse> categories(Long ownerUserId) {
        Shop shop = shopService.requireOwnedShop(ownerUserId);
        return categoryMapper.selectList(new LambdaQueryWrapper<MenuCategory>()
                        .eq(MenuCategory::getShopId, shop.getId())
                        .orderByAsc(MenuCategory::getSortOrder))
                .stream().map(this::toCategoryResponse).toList();
    }

    public CategoryDtos.CategoryResponse createCategory(Long ownerUserId, CategoryDtos.CategoryRequest request) {
        Shop shop = shopService.requireOwnedShop(ownerUserId);
        String name = normalizeCategoryName(request.name());
        checkCategoryNameUnique(shop.getId(), null, name);
        MenuCategory category = new MenuCategory();
        category.setShopId(shop.getId());
        category.setName(name);
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        category.setStatus(1);
        categoryMapper.insert(category);
        menuCacheInvalidator.evictMenuAfterCommit(shop.getId());
        return toCategoryResponse(category);
    }

    public CategoryDtos.CategoryResponse updateCategory(Long ownerUserId, Long categoryId, CategoryDtos.CategoryRequest request) {
        MenuCategory category = requireOwnedCategory(ownerUserId, categoryId);
        String name = normalizeCategoryName(request.name());
        checkCategoryNameUnique(category.getShopId(), categoryId, name);
        category.setName(name);
        category.setSortOrder(request.sortOrder() == null ? category.getSortOrder() : request.sortOrder());
        categoryMapper.updateById(category);
        menuCacheInvalidator.evictMenuAfterCommit(category.getShopId());
        return toCategoryResponse(categoryMapper.selectById(categoryId));
    }

    public void deleteCategory(Long ownerUserId, Long categoryId) {
        MenuCategory category = requireOwnedCategory(ownerUserId, categoryId);
        Long dishCount = dishMapper.selectCount(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getCategoryId, categoryId)
                .notIn(Dish::getStatus, StatusConstants.DISH_DELETE));

        if (dishCount != null && dishCount > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前分类中存在菜品，请保证当前分类为空才可删除。");
        }

        categoryMapper.deleteById(categoryId);
        menuCacheInvalidator.evictMenuAfterCommit(category.getShopId());
    }

    public PageResponse<DishDtos.DishResponse> shopDishes(Long ownerUserId, int page, int pageSize, Long categoryId, String keyword, String status) {
        Shop shop = shopService.requireOwnedShop(ownerUserId);
        if (categoryId != null) {
            requireOwnedCategory(ownerUserId, categoryId);
        }
        String normalizedStatus = normalizeDishStatus(status, false);
        Page<Dish> result = dishMapper.selectPage(new Page<>(page, pageSize), new LambdaQueryWrapper<Dish>()
                .eq(Dish::getShopId, shop.getId())
                .eq(categoryId != null, Dish::getCategoryId, categoryId)
                .eq(StringUtils.hasText(normalizedStatus), Dish::getStatus, normalizedStatus)
                .like(StringUtils.hasText(keyword), Dish::getName, keyword)
                .orderByAsc(Dish::getSortOrder));
        return new PageResponse<>(result.getRecords().stream().map(this::toResponse).toList(), page, pageSize, result.getTotal());
    }

    public DishDtos.DishResponse createDish(Long ownerUserId, DishDtos.DishRequest request) {
        Shop shop = shopService.requireOwnedShop(ownerUserId);
        checkDishNameUnique(shop.getId(), null, request.name().trim());
        Dish dish = new Dish();
        dish.setShopId(shop.getId());
        apply(shop.getId(), dish, request);
        dish.setSalesCount(0);
        dishMapper.insert(dish);
        dishOptionService.replace(dish.getId(), request.optionGroups());
        menuCacheInvalidator.evictMenuAfterCommit(shop.getId());
        return toResponse(dish);
    }

    public DishDtos.DishResponse updateDish(Long ownerUserId, Long dishId, DishDtos.DishRequest request) {
        Dish dish = requireOwnedDish(ownerUserId, dishId);
        checkDishNameUnique(dish.getShopId(), dishId, request.name().trim());
        apply(dish.getShopId(), dish, request);
        dishMapper.updateById(dish);
        dishOptionService.replace(dishId, request.optionGroups());
        menuCacheInvalidator.evictMenuAfterCommit(dish.getShopId());
        return toResponse(dishMapper.selectById(dishId));
    }

    public DishDtos.DishResponse updateStatus(Long ownerUserId, Long dishId, String status) {
        Dish dish = requireOwnedDish(ownerUserId, dishId);
        dish.setStatus(normalizeDishStatus(status, true));
        dishMapper.updateById(dish);
        menuCacheInvalidator.evictMenuAfterCommit(dish.getShopId());
        return toResponse(dishMapper.selectById(dishId));
    }

    public void deleteDish(Long ownerUserId, Long dishId) {
        Dish dish = requireOwnedDish(ownerUserId, dishId);
        dish.setStatus(StatusConstants.DISH_DELETE);
        dishMapper.updateById(dish);
        menuCacheInvalidator.evictMenuAfterCommit(dish.getShopId());
    }

    public Dish requireOwnedDish(Long ownerUserId, Long dishId) {
        Shop shop = shopService.requireOwnedShop(ownerUserId);
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null || !shop.getId().equals(dish.getShopId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Dish not found");
        }
        return dish;
    }

    private MenuCategory requireOwnedCategory(Long ownerUserId, Long categoryId) {
        Shop shop = shopService.requireOwnedShop(ownerUserId);
        MenuCategory category = categoryMapper.selectById(categoryId);
        if (category == null || !shop.getId().equals(category.getShopId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Category not found");
        }
        return category;
    }

    private void apply(Long shopId, Dish dish, DishDtos.DishRequest request) {
        if (request.categoryId() != null) {
            MenuCategory category = categoryMapper.selectById(request.categoryId());
            if (category == null || !shopId.equals(category.getShopId()) || category.getStatus() == null || category.getStatus() != 1) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid dish category");
            }
        }
        dish.setCategoryId(request.categoryId());
        dish.setName(request.name().trim());
        dish.setDescription(trimToNull(request.description()));
        dish.setPrice(request.price());
        dish.setImageUrl(trimToNull(request.imageUrl()));
        dish.setStock(request.stock());
        dish.setStatus(normalizeDishStatus(request.status(), false));
        dish.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
    }

    private String normalizeDishStatus(String status, boolean required) {
        if (!StringUtils.hasText(status)) {
            if (required) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Dish status is required");
            }
            return StatusConstants.DISH_ON_SALE;
        }
        String normalized = status.trim();
        if (!StatusConstants.isDishStatus(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid dish status");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private CategoryDtos.CategoryResponse toCategoryResponse(MenuCategory category) {
        return new CategoryDtos.CategoryResponse(category.getId(), category.getName(), category.getSortOrder(), category.getStatus());
    }

    public DishDtos.DishResponse toResponse(Dish dish) {
        return new DishDtos.DishResponse(dish.getId(), dish.getShopId(), dish.getCategoryId(), dish.getName(),
                dish.getDescription(), dish.getPrice(), dish.getImageUrl(), dish.getStock(), dish.getSalesCount(),
                dish.getStatus(), dish.getSortOrder(), dish.getRecommendOrder(),
                dishOptionService.groups(dish.getId()));
    }

    private void checkDishNameUnique(Long shopId, Long dishId, String name) {
        LambdaQueryWrapper<Dish> wrapper = new LambdaQueryWrapper<Dish>()
                .eq(Dish::getShopId, shopId)
                .eq(Dish::getName, name)
                .ne(Dish::getStatus, StatusConstants.DISH_DELETE);

        // 如果是更新操作，需要排除当前正在修改的这道菜自身
        if (dishId != null) {
            wrapper.ne(Dish::getId, dishId);
        }

        Long count = dishMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "菜品名称已存在");
        }
    }

    /**
     * 根据商家 ID 列表批量获取首页预览菜品（每个商家最多 5 个）
     */
    public Map<Long, List<DishDtos.DishResponse>> getPreviewDishesByShopIds(List<Long> shopIds) {
        if (shopIds == null || shopIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 1. 查询这些店铺下所有在售的菜品，并按 SortOrder 升序排列保证推荐优先级
        List<Dish> dishes = dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .in(Dish::getShopId, shopIds)
                .eq(Dish::getStatus, StatusConstants.DISH_ON_SALE)
                .orderByAsc(Dish::getSortOrder)
        );

        // 2. 按 shopId 分组，每个店铺最多截取前 5 个菜品，并转换为简化版的 DishResponse
        return dishes.stream()
                .collect(Collectors.groupingBy(
                        Dish::getShopId,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> list.stream()
                                        .limit(5)
                                        .map(this::toSimpleResponse)
                                        .toList()
                        )
                ));
    }

    /**
     * 简化版的 DTO 转换，避免首页预览产生 N+1 选项组查询
     */
    public DishDtos.DishResponse toSimpleResponse(Dish dish) {
        return new DishDtos.DishResponse(
                dish.getId(),
                dish.getShopId(),
                dish.getCategoryId(),
                dish.getName(),
                dish.getDescription(),
                dish.getPrice(),
                dish.getImageUrl(),
                dish.getStock(),
                dish.getSalesCount(),
                dish.getStatus(),
                dish.getSortOrder(),
                dish.getRecommendOrder(),
                Collections.emptyList() // 首页不需要渲染菜品规格，直接返回空列表
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateShowcaseDishes(Long ownerUserId, List<Long> dishIds) {
        Shop shop = shopService.requireOwnedShop(ownerUserId);

        // 重置当前商家的所有推荐排序为 0
        dishMapper.update(null, new LambdaUpdateWrapper<Dish>()
                .eq(Dish::getShopId, shop.getId())
                .set(Dish::getRecommendOrder, 0));

        // 如果传了空列表，直接返回
        if (dishIds == null || dishIds.isEmpty()) {
            return;
        }

        // 依次更新传入菜品的 recommend_order (1~8)
        int order = 1;
        for (Long dishId : dishIds) {
            dishMapper.update(null, new LambdaUpdateWrapper<Dish>()
                    .eq(Dish::getId, dishId)
                    .eq(Dish::getShopId, shop.getId())
                    .set(Dish::getRecommendOrder, order++));
        }
        menuCacheInvalidator.evictMenuAfterCommit(shop.getId());
    }

    private String normalizeCategoryName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分类名称不能为空");
        }
        String normalized = name.trim();
        if (normalized.length() > 30) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分类名称不能超过30个字符");
        }
        return normalized;
    }

    private void checkCategoryNameUnique(Long shopId, Long categoryId, String name) {
        LambdaQueryWrapper<MenuCategory> wrapper = new LambdaQueryWrapper<MenuCategory>()
                .eq(MenuCategory::getShopId, shopId)
                .eq(MenuCategory::getName, name);

        // 如果是更新操作，排除当前正在修改的类别自身
        if (categoryId != null) {
            wrapper.ne(MenuCategory::getId, categoryId);
        }

        Long count = categoryMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该类别名称已存在");
        }
    }
}
