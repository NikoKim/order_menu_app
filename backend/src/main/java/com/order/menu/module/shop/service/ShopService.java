package com.order.menu.module.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.order.menu.common.constant.StatusConstants;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.common.response.PageResponse;
import com.order.menu.module.category.entity.MenuCategory;
import com.order.menu.module.category.mapper.MenuCategoryMapper;
import com.order.menu.module.dish.entity.Dish;
import com.order.menu.module.dish.mapper.DishMapper;
import com.order.menu.module.dish.service.DishOptionService;
import com.order.menu.module.shop.dto.ShopDtos;
import com.order.menu.module.shop.entity.Shop;
import com.order.menu.module.shop.mapper.ShopMapper;
import java.util.List;
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

    public PageResponse<ShopDtos.ShopResponse> list(int page, int pageSize, String keyword, String familyCode) {
        String safeKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        String safeFamilyCode = StringUtils.hasText(familyCode) ? familyCode.trim().toUpperCase() : null;
        Page<Shop> result = shopMapper.selectPage(new Page<>(page, pageSize), new LambdaQueryWrapper<Shop>()
                .eq(Shop::getStatus, 1)
                .inSql(Shop::getOwnerUserId, "select id from users where status = 1")
                .eq(StringUtils.hasText(safeFamilyCode), Shop::getFamilyCode, safeFamilyCode)
                .eq(!StringUtils.hasText(safeFamilyCode), Shop::getVisibilityType, "PUBLIC")
                .like(StringUtils.hasText(safeKeyword), Shop::getName, safeKeyword)
                .orderByDesc(Shop::getId));
        return new PageResponse<>(result.getRecords().stream().map(this::toResponse).toList(), page, pageSize, result.getTotal());
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
        return toResponse(requireById(shopId));
    }

    public ShopDtos.ShopResponse myShop(Long ownerUserId) {
        return toResponse(requireOwnedShop(ownerUserId));
    }

    public ShopDtos.ShopResponse updateMyShop(Long ownerUserId, ShopDtos.ShopUpdateRequest request) {
        Shop shop = requireOwnedShop(ownerUserId);
        shop.setName(request.name().trim());
        shop.setDescription(trimToNull(request.description()));
        shop.setCoverImageUrl(trimToNull(request.coverImageUrl()));
        shop.setAddress(trimToNull(request.address()));
        shop.setPhone(trimToNull(request.phone()));
        String businessStatus = StringUtils.hasText(request.businessStatus()) ? request.businessStatus().trim() : StatusConstants.BUSINESS_OPEN;
        if (!StatusConstants.isBusinessStatus(businessStatus)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid shop business status");
        }
        shop.setBusinessStatus(businessStatus);
        String visibilityType = StringUtils.hasText(request.visibilityType()) ? request.visibilityType().trim() : "PUBLIC";
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
        return toResponse(shopMapper.selectById(shop.getId()));
    }

    public ShopDtos.MenuResponse menu(Long shopId) {
        Shop shop = requireById(shopId);
        List<MenuCategory> categories = categoryMapper.selectList(new LambdaQueryWrapper<MenuCategory>()
                .eq(MenuCategory::getShopId, shopId)
                .eq(MenuCategory::getStatus, 1)
                .orderByAsc(MenuCategory::getSortOrder));
        List<ShopDtos.CategoryMenuResponse> categoryResponses = categories.stream().map(category -> {
            List<com.order.menu.module.dish.dto.DishDtos.DishResponse> dishes = dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                            .eq(Dish::getShopId, shopId)
                            .eq(Dish::getCategoryId, category.getId())
                            .eq(Dish::getStatus, StatusConstants.DISH_ON_SALE)
                            .orderByAsc(Dish::getSortOrder))
                    .stream().map(this::toDishResponse).toList();
            return new ShopDtos.CategoryMenuResponse(category.getId(), category.getName(), dishes);
        }).toList();
        return new ShopDtos.MenuResponse(toResponse(shop), categoryResponses);
    }

    public ShopDtos.ShopResponse toResponse(Shop shop) {
        return new ShopDtos.ShopResponse(shop.getId(), shop.getName(), shop.getDescription(), shop.getCoverImageUrl(),
                shop.getAddress(), shop.getPhone(), shop.getBusinessStatus(),
                shop.getVisibilityType() == null ? "PUBLIC" : shop.getVisibilityType(),
                shop.getFamilyName(), shop.getFamilyCode(),
                shop.getFamilyJoinMode() == null ? "OPEN" : shop.getFamilyJoinMode(),
                shop.getFamilyImageUrl());
    }

    private com.order.menu.module.dish.dto.DishDtos.DishResponse toDishResponse(Dish dish) {
        return new com.order.menu.module.dish.dto.DishDtos.DishResponse(dish.getId(), dish.getShopId(), dish.getCategoryId(), dish.getName(),
                dish.getDescription(), dish.getPrice(), dish.getImageUrl(), dish.getStock(), dish.getSalesCount(),
                dish.getStatus(), dish.getSortOrder(), dishOptionService.groups(dish.getId()));
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
