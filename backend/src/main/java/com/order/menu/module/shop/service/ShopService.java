package com.order.menu.module.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.order.menu.common.constant.StatusConstants;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.common.response.PageResponse;
import com.order.menu.module.category.entity.MenuCategory;
import com.order.menu.module.category.mapper.MenuCategoryMapper;
import com.order.menu.module.dish.dto.DishDtos;
import com.order.menu.module.dish.entity.Dish;
import com.order.menu.module.dish.mapper.DishMapper;
import com.order.menu.module.dish.service.DishOptionService;
import com.order.menu.module.shop.dto.ShopDtos;
import com.order.menu.module.shop.entity.Shop;
import com.order.menu.module.shop.mapper.ShopMapper;
import com.order.menu.module.shop.service.cache.MenuCacheInvalidator;
import com.order.menu.module.shop.service.cache.ShopCachePort;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.order.menu.websocket.WsPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ShopService {
    private final ShopMapper shopMapper;
    private final MenuCategoryMapper categoryMapper;
    private final DishMapper dishMapper;
    private final DishOptionService dishOptionService;
    private final WsPushService wsPushService;
    private final ShopCachePort shopCachePort;
    private final MenuCacheInvalidator menuCacheInvalidator;

    public PageResponse<ShopDtos.ShopResponse> list(int page, int pageSize, String keyword, String familyCode) {
        String safeKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        String safeFamilyCode = StringUtils.hasText(familyCode) ? familyCode.trim().toUpperCase() : null;

        Page<Shop> result = shopMapper.selectPage(new Page<>(page, pageSize), new LambdaQueryWrapper<Shop>()
                .eq(Shop::getStatus, 1)
                .inSql(Shop::getOwnerUserId, "select id from users where status = 1")
                .eq(StringUtils.hasText(safeFamilyCode), Shop::getFamilyCode, safeFamilyCode)
                .and(!StringUtils.hasText(safeFamilyCode), wrapper -> wrapper
                        .eq(Shop::getVisibilityType, "PUBLIC")
                        .or()
                        .isNull(Shop::getFamilyCode))
                .like(StringUtils.hasText(safeKeyword), Shop::getName, safeKeyword)
                .orderByDesc(Shop::getId));

        List<Shop> shops = result.getRecords();

        // 提取商家 ID，批量查询预览菜品
        List<Long> shopIds = shops.stream().map(Shop::getId).toList();
        Map<Long, List<com.order.menu.module.dish.dto.DishDtos.DishResponse>> previewMap = getPreviewDishes(shopIds);

        // 组装数据
        List<ShopDtos.ShopResponse> responses = shops.stream()
                .map(shop -> toResponse(shop, previewMap.getOrDefault(shop.getId(), Collections.emptyList())))
                .toList();

        return new PageResponse<>(responses, page, pageSize, result.getTotal());
    }

    // 重载 toResponse 方法，支持传入 previewDishes
    public ShopDtos.ShopResponse toResponse(Shop shop) {
        // 默认不传菜品时返回空列表，供详情、我的店铺等接口复用
        return toResponse(shop, Collections.emptyList());
    }

    public ShopDtos.ShopResponse toResponse(Shop shop, List<com.order.menu.module.dish.dto.DishDtos.DishResponse> previewDishes) {
        return new ShopDtos.ShopResponse(shop.getId(), shop.getName(), shop.getDescription(), shop.getCoverImageUrl(),
                shop.getAddress(), shop.getPhone(), shop.getBusinessStatus(),
                shop.getVisibilityType() == null ? "PUBLIC" : shop.getVisibilityType(),
                shop.getFamilyName(), shop.getFamilyCode(),
                shop.getFamilyJoinMode() == null ? "OPEN" : shop.getFamilyJoinMode(),
                shop.getFamilyImageUrl(),
                previewDishes); // 注入菜品列表
    }

    // 批量查询商家推荐菜品的方法
    private Map<Long, List<DishDtos.DishResponse>> getPreviewDishes(List<Long> shopIds) {
        if (shopIds == null || shopIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 修改查询排序逻辑：优先按照 recommend_order 排序（有推荐的优先），
        // 当 recommend_order 为 0 时，使用 RAND() 随机补齐。
        List<Dish> dishes = dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .in(Dish::getShopId, shopIds)
                .eq(Dish::getStatus, StatusConstants.DISH_ON_SALE)
                // 使用 last 注入自定义 SQL 排序
                .last("ORDER BY CASE WHEN recommend_order > 0 THEN 0 ELSE 1 END, recommend_order ASC, RAND()")
        );

        return dishes.stream()
                .collect(Collectors.groupingBy(
                        Dish::getShopId,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> list.stream()
                                        .limit(8) // 截取 8 个
                                        .map(this::toSimpleDishResponse)
                                        .toList()
                        )
                ));
    }

    // 更新简化版的 DTO 转换
    private com.order.menu.module.dish.dto.DishDtos.DishResponse toSimpleDishResponse(Dish dish) {
        return new com.order.menu.module.dish.dto.DishDtos.DishResponse(dish.getId(), dish.getShopId(), dish.getCategoryId(), dish.getName(),
                dish.getDescription(), dish.getPrice(), dish.getImageUrl(), dish.getStock(), dish.getSalesCount(),
                dish.getStatus(), dish.getSortOrder(), dish.getRecommendOrder(),
                Collections.emptyList());
    }

    public Shop requireById(Long shopId) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null || shop.getStatus() == null || shop.getStatus() == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Shop not found");
        }
        Long enabledOwnerCount = shopMapper.selectCount(new LambdaQueryWrapper<Shop>()
                .eq(Shop::getId, shopId)
                .inSql(Shop::getOwnerUserId, "select id from users where status = 1"));
        if (enabledOwnerCount == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Shop not found");
        }
        return shop;
    }

    public Shop requireOwnedShop(Long ownerUserId) {
        Shop shop = shopMapper.selectOne(new LambdaQueryWrapper<Shop>().eq(Shop::getOwnerUserId, ownerUserId).last("limit 1"));
        if (shop == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Current shop account is not bound to a shop");
        }
        return shop;
    }

    public ShopDtos.ShopResponse detail(Long shopId) {
        Shop shop = requireById(shopId);
        Optional<ShopDtos.ShopResponse> cached = shopCachePort.getShopDetail(shopId);
        if (cached.isPresent()) {
            return cached.get();
        }
        ShopDtos.ShopResponse response = toResponse(shop);
        shopCachePort.putShopDetail(shopId, response);
        return response;
    }

    public ShopDtos.ShopResponse myShop(Long ownerUserId) {
        Shop shop = requireOwnedShop(ownerUserId);
        Optional<ShopDtos.ShopResponse> cached = shopCachePort.getShopDetail(shop.getId());
        if (cached.isPresent()) {
            return cached.get();
        }
        ShopDtos.ShopResponse response = toResponse(shop);
        shopCachePort.putShopDetail(shop.getId(), response);
        return response;
    }

    public ShopDtos.ShopResponse updateMyShop(Long ownerUserId, ShopDtos.ShopUpdateRequest request) {
        Shop shop = requireOwnedShop(ownerUserId);

        String oldBusinessStatus = shop.getBusinessStatus();

        shop.setName(request.name().trim());
        shop.setDescription(trimToNull(request.description()));
        shop.setCoverImageUrl(trimToNull(request.coverImageUrl()));
        shop.setAddress(trimToNull(request.address()));
        shop.setPhone(trimToNull(request.phone()));

        String businessStatus = StringUtils.hasText(request.businessStatus())
                ? request.businessStatus().trim()
                : StatusConstants.BUSINESS_OPEN;

        if (!StatusConstants.isBusinessStatus(businessStatus)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid shop business status");
        }

        shop.setBusinessStatus(businessStatus);

        String visibilityType = StringUtils.hasText(request.visibilityType())
                ? request.visibilityType().trim()
                : "PUBLIC";

        if (!"PUBLIC".equals(visibilityType) && !"FAMILY".equals(visibilityType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid visibility type");
        }

        shop.setVisibilityType(visibilityType);
        shop.setFamilyName(trimToNull(request.familyName()));
        shop.setFamilyJoinMode(normalizeJoinMode(request.familyJoinMode()));
        shop.setFamilyImageUrl(trimToNull(request.familyImageUrl()));

        if ("FAMILY".equals(visibilityType) && !StringUtils.hasText(shop.getFamilyCode())) {
            shop.setFamilyCode(generateFamilyCode());
        }

        if ("PUBLIC".equals(visibilityType)) {
            shop.setFamilyName(null);
            shop.setFamilyJoinMode("OPEN");
            shop.setFamilyImageUrl(null);
        }

        shopMapper.updateById(shop);

        menuCacheInvalidator.evictShopAfterCommit(shop.getId());

        Shop updated = shopMapper.selectById(shop.getId());

        if (!Objects.equals(oldBusinessStatus, updated.getBusinessStatus())) {
            wsPushService.pushShopBusinessStatusChanged(
                    updated.getId(),
                    updated.getBusinessStatus()
            );
        } else {
            wsPushService.pushShopProfileChanged(updated.getId());
        }

        return toResponse(updated);
    }

    public ShopDtos.MenuResponse menu(Long shopId) {
        Shop shop = requireById(shopId);
        Optional<ShopDtos.MenuResponse> cached = shopCachePort.getMenu(shopId);
        if (cached.isPresent()) {
            return cached.get();
        }
        ShopDtos.MenuResponse response = loadMenuFromDatabase(shop);
        shopCachePort.putMenu(shopId, response);
        return response;
    }

    private ShopDtos.MenuResponse loadMenuFromDatabase(Shop shop) {
        Long shopId = shop.getId();
        List<MenuCategory> categories = categoryMapper.selectList(new LambdaQueryWrapper<MenuCategory>()
                .eq(MenuCategory::getShopId, shopId)
                .eq(MenuCategory::getStatus, 1)
                .orderByAsc(MenuCategory::getSortOrder));
        List<Dish> dishes = dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getShopId, shopId)
                .eq(Dish::getStatus, StatusConstants.DISH_ON_SALE)
                .orderByAsc(Dish::getCategoryId)
                .orderByAsc(Dish::getSortOrder));
        Map<Long, List<Dish>> dishesByCategory = dishes.stream()
                .collect(Collectors.groupingBy(Dish::getCategoryId, Collectors.toList()));
        Map<Long, List<DishDtos.OptionGroupResponse>> optionsByDish = dishOptionService
                .groupsByDishIds(dishes.stream().map(Dish::getId).toList());
        List<ShopDtos.CategoryMenuResponse> categoryResponses = categories.stream().map(category -> {
            List<DishDtos.DishResponse> categoryDishes = dishesByCategory
                    .getOrDefault(category.getId(), List.of())
                    .stream()
                    .map(dish -> toDishResponse(dish, optionsByDish.getOrDefault(dish.getId(), List.of())))
                    .toList();
            return new ShopDtos.CategoryMenuResponse(category.getId(), category.getName(), categoryDishes);
        }).toList();
        return new ShopDtos.MenuResponse(toResponse(shop), categoryResponses);
    }

    private DishDtos.DishResponse toDishResponse(Dish dish, List<DishDtos.OptionGroupResponse> optionGroups) {
        return new DishDtos.DishResponse(dish.getId(), dish.getShopId(), dish.getCategoryId(), dish.getName(),
                dish.getDescription(), dish.getPrice(), dish.getImageUrl(), dish.getStock(), dish.getSalesCount(),
                dish.getStatus(), dish.getSortOrder(), dish.getRecommendOrder(), optionGroups);
    }

    private String normalizeJoinMode(String value) {
        if (!StringUtils.hasText(value)) {
            return "OPEN";
        }
        String normalized = value.trim();
        if (!"OPEN".equals(normalized) && !"APPROVAL".equals(normalized) && !"CLOSED".equals(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid family join mode");
        }
        return normalized;
    }

    private String generateFamilyCode() {
        String alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        for (int attempt = 0; attempt < 30; attempt++) {
            StringBuilder code = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                code.append(alphabet.charAt(java.util.concurrent.ThreadLocalRandom.current().nextInt(alphabet.length())));
            }
            Long count = shopMapper.selectCount(new LambdaQueryWrapper<Shop>().eq(Shop::getFamilyCode, code.toString()));
            if (count == 0) {
                return code.toString();
            }
        }
        throw new BusinessException(ErrorCode.SERVER_ERROR, "Failed to generate family code");
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
