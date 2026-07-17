package com.order.menu.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.module.user.dto.UserDtos;
import com.order.menu.module.user.entity.User;
import com.order.menu.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;

    public User requireEnabled(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }
        return user;
    }

    public UserDtos.CurrentUserResponse current(Long userId) {
        return toResponse(requireEnabled(userId));
    }

    public UserDtos.CurrentUserResponse updateMe(Long userId, UserDtos.UpdateMeRequest request) {
        User user = requireEnabled(userId);
        if (StringUtils.hasText(request.nickname())) {
            String nickname = request.nickname().trim();
            Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getNickname, nickname)
                    .eq(User::getRole, user.getRole())
                    .ne(User::getId, user.getId()));
            if (count > 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "昵称已存在");
            }
            user.setNickname(nickname);
        }
        if (request.phone() != null) {
            user.setPhone(emptyToNull(request.phone()));
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(emptyToNull(request.avatarUrl()));
        }
        if (request.allowPrivateChat() != null) {
            user.setAllowPrivateChat(request.allowPrivateChat());
        }
        if (request.autoJoinFamilyChat() != null) {
            user.setAutoJoinFamilyChat(request.autoJoinFamilyChat());
        }
        if (request.showJoinedFamilies() != null) {
            user.setShowJoinedFamilies(request.showJoinedFamilies());
        }
        if (request.showPhone() != null) {
            user.setShowPhone(request.showPhone());
        }
        if (request.momentProfileVisibility() != null) {
            if (!request.momentProfileVisibility().matches("PUBLIC|CHAT_FRIENDS|FAMILY|PRIVATE")) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的动态页可见范围");
            }
            user.setMomentProfileVisibility(request.momentProfileVisibility());
        }
        userMapper.updateById(user);
        return toResponse(userMapper.selectById(user.getId()));
    }

    public UserDtos.CurrentUserResponse toResponse(User user) {
        return new UserDtos.CurrentUserResponse(
                user.getId(),
                user.getNickname(),
                user.getRole(),
                user.getAvatarUrl(),
                user.getPhone(),
                user.getAllowPrivateChat(),
                user.getAutoJoinFamilyChat(),
                user.getShowJoinedFamilies(),
                user.getShowPhone(), user.getMomentProfileVisibility());
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
