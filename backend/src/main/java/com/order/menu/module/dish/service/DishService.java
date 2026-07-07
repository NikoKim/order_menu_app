package com.order.menu.module.dish.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DishService {
    private final DishMapper dishMapper;
    private final MenuCategoryMapper categoryMapper;
    private final ShopService shopService;
    private final DishOptionService dishOptionService;

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
        MenuCategory category = new MenuCategory();
        category.setShopId(shop.getId());
        category.setName(request.name().trim());
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        category.setStatus(1);
        categoryMapper.insert(category);
        return toCategoryResponse(category);
    }

    public CategoryDtos.CategoryResponse updateCategory(Long ownerUserId, Long categoryId, CategoryDtos.CategoryRequest request) {
        MenuCategory category = requireOwnedCategory(ownerUserId, categoryId);
        category.setName(request.name().trim());
        category.setSortOrder(request.sortOrder() == null ? category.getSortOrder() : request.sortOrder());
        categoryMapper.updateById(category);
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
        return toResponse(dish);
    }

    public DishDtos.DishResponse updateDish(Long ownerUserId, Long dishId, DishDtos.DishRequest request) {
        Dish dish = requireOwnedDish(ownerUserId, dishId);
        checkDishNameUnique(dish.getShopId(), dishId, request.name().trim());
        apply(dish.getShopId(), dish, request);
        dishMapper.updateById(dish);
        dishOptionService.replace(dishId, request.optionGroups());
        return toResponse(dishMapper.selectById(dishId));
    }

    public DishDtos.DishResponse updateStatus(Long ownerUserId, Long dishId, String status) {
        Dish dish = requireOwnedDish(ownerUserId, dishId);
        dish.setStatus(normalizeDishStatus(status, true));
        dishMapper.updateById(dish);
        return toResponse(dishMapper.selectById(dishId));
    }

    public void deleteDish(Long ownerUserId, Long dishId) {
        Dish dish = requireOwnedDish(ownerUserId, dishId);
        dish.setStatus(StatusConstants.DISH_DELETE);
        dishMapper.updateById(dish);
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
                dish.getStatus(), dish.getSortOrder(), dishOptionService.groups(dish.getId()));
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
}
