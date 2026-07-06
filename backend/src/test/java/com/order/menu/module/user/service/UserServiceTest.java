package com.order.menu.module.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.order.menu.module.user.dto.UserDtos;
import com.order.menu.module.user.entity.User;
import com.order.menu.module.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserMapper userMapper;

    @Test
    void updateMeChecksNicknameWithinCurrentUserRole() {
        User current = new User();
        current.setId(7L);
        current.setNickname("old");
        current.setRole("SHOP");
        current.setStatus(1);
        when(userMapper.selectById(7L)).thenReturn(current);
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        UserService service = new UserService(userMapper);
        service.updateMe(7L, new UserDtos.UpdateMeRequest("nikokim", null, null));

        ArgumentCaptor<Wrapper<User>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        org.mockito.Mockito.verify(userMapper).selectCount(wrapper.capture());
        AbstractWrapper<User, ?, ?> query = (AbstractWrapper<User, ?, ?>) wrapper.getValue();
        assertEquals(11, query.getExpression().getNormal().size());
    }
}
