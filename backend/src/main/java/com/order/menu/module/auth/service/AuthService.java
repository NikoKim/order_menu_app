package com.order.menu.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.order.menu.common.constant.RoleConstants;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.module.auth.dto.AuthDtos;
import com.order.menu.module.user.entity.User;
import com.order.menu.module.user.mapper.UserMapper;
import com.order.menu.module.user.service.UserService;
import com.order.menu.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;

    public AuthDtos.RegisterResponse registerCustomer(AuthDtos.CustomerRegisterRequest request) {
        String nickname = request.nickname().trim();
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getNickname, nickname)
                .eq(User::getRole, RoleConstants.CUSTOMER));
        if (count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "昵称已存在");
        }
        User user = new User();
        user.setNickname(nickname);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(RoleConstants.CUSTOMER);
        user.setStatus(1);
        userMapper.insert(user);
        return new AuthDtos.RegisterResponse(user.getId(), user.getNickname());
    }

    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getNickname, request.nickname().trim())
                .eq(User::getRole, request.role()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }
        String token = jwtTokenProvider.createToken(user.getId(), user.getRole(), user.getNickname());
        return new AuthDtos.LoginResponse(token, userService.toResponse(user));
    }
}
