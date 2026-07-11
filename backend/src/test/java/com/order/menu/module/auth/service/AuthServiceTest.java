package com.order.menu.module.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.order.menu.module.auth.dto.AuthDtos;
import com.order.menu.module.user.entity.User;
import com.order.menu.module.user.mapper.UserMapper;
import com.order.menu.module.user.service.UserService;
import com.order.menu.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserService userService;

    @Test
    void customerRegisterChecksNicknameWithinCustomerRole() {
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("secret123")).thenReturn("hash");

        AuthService service = new AuthService(userMapper, passwordEncoder, jwtTokenProvider, userService);
        service.registerCustomer(new AuthDtos.CustomerRegisterRequest("nikokim", "secret123"));

        ArgumentCaptor<Wrapper<User>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        org.mockito.Mockito.verify(userMapper).selectCount(wrapper.capture());
        AbstractWrapper<User, ?, ?> query = (AbstractWrapper<User, ?, ?>) wrapper.getValue();
        assertEquals(7, query.getExpression().getNormal().size());
    }

    @Test
    void customerRegisterPersistsEnabledChatPermissionsByDefault() {
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("secret123")).thenReturn("hash");

        AuthService service = new AuthService(userMapper, passwordEncoder, jwtTokenProvider, userService);
        service.registerCustomer(new AuthDtos.CustomerRegisterRequest("nikokim", "secret123"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertTrue(savedUser.getAllowPrivateChat());
        assertTrue(savedUser.getAutoJoinFamilyChat());
    }
}
