package com.order.menu.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.order.menu.common.constant.RoleConstants;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.common.response.PageResponse;
import com.order.menu.module.admin.dto.AdminDtos;
import com.order.menu.module.shop.entity.Shop;
import com.order.menu.module.shop.mapper.ShopMapper;
import com.order.menu.module.user.entity.User;
import com.order.menu.module.user.mapper.UserMapper;
import com.order.menu.module.moment.service.cache.MomentAuxiliaryCachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminUserService {
    private final UserMapper userMapper;
    private final ShopMapper shopMapper;
    private final PasswordEncoder passwordEncoder;
    private MomentAuxiliaryCachePort momentAuxiliaryCache;

    @Autowired(required = false)
    void setMomentAuxiliaryCache(MomentAuxiliaryCachePort momentAuxiliaryCache) {
        this.momentAuxiliaryCache = momentAuxiliaryCache;
    }

    public PageResponse<AdminDtos.UserAccountResponse> list(String role, int page, int pageSize, String keyword) {
        String safeRole = StringUtils.hasText(role) ? role.trim() : null;
        String safeKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Page<User> result = userMapper.selectPage(new Page<>(page, pageSize), new LambdaQueryWrapper<User>()
                .in(User::getRole, RoleConstants.CUSTOMER, RoleConstants.SHOP)
                .eq(StringUtils.hasText(safeRole), User::getRole, safeRole)
                .like(StringUtils.hasText(safeKeyword), User::getNickname, safeKeyword)
                .orderByDesc(User::getId));
        return new PageResponse<>(result.getRecords().stream().map(this::toResponse).toList(),
                page, pageSize, result.getTotal());
    }

    @Transactional
    public AdminDtos.UserAccountResponse create(AdminDtos.CreateUserRequest request) {
        String nickname = request.nickname().trim();
        String role = request.role().trim();
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getNickname, nickname)
                .eq(User::getRole, role));
        if (count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "账号已存在");
        }

        User user = new User();
        user.setNickname(nickname);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(role);
        user.setPhone(trimToNull(request.phone()));
        user.setStatus(1);
        userMapper.insert(user);

        if (RoleConstants.SHOP.equals(role)) {
            Shop shop = new Shop();
            shop.setOwnerUserId(user.getId());
            shop.setName(StringUtils.hasText(request.shopName()) ? request.shopName().trim() : nickname + "的店铺");
            shop.setBusinessStatus("OPEN");
            shop.setStatus(1);
            shopMapper.insert(shop);
        }
        return toResponse(userMapper.selectById(user.getId()));
    }

    public AdminDtos.UserAccountResponse updateStatus(Long userId, AdminDtos.StatusRequest request) {
        Integer status = request.status();
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "状态只能为0或1");
        }
        User user = userMapper.selectById(userId);
        if (user == null || RoleConstants.ADMIN.equals(user.getRole())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "账号不存在");
        }
        user.setStatus(status);
        userMapper.updateById(user);
        if (momentAuxiliaryCache != null) {
            momentAuxiliaryCache.evictAuthor(userId);
            momentAuxiliaryCache.evictMentionSearches();
        }
        return toResponse(userMapper.selectById(userId));
    }

    private AdminDtos.UserAccountResponse toResponse(User user) {
        return new AdminDtos.UserAccountResponse(user.getId(), user.getNickname(), user.getRole(),
                user.getPhone(), user.getStatus(), user.getCreatedAt());
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
