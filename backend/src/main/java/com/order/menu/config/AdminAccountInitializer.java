package com.order.menu.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.order.menu.common.constant.RoleConstants;
import com.order.menu.module.user.entity.User;
import com.order.menu.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {
    private static final String ADMIN_NICKNAME = "nikokim";
    private static final String ADMIN_PASSWORD = "1714672038qwe";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getNickname, ADMIN_NICKNAME)
                .eq(User::getRole, RoleConstants.ADMIN)
                .last("limit 1"));
        if (existing != null) {
            if (existing.getStatus() == null || existing.getStatus() != 1) {
                existing.setStatus(1);
                userMapper.updateById(existing);
            }
            return;
        }
        User admin = new User();
        admin.setNickname(ADMIN_NICKNAME);
        admin.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setRole(RoleConstants.ADMIN);
        admin.setStatus(1);
        userMapper.insert(admin);
    }
}
