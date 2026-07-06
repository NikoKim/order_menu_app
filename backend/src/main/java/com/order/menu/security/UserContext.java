package com.order.menu.security;

import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;

public final class UserContext {
    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        return user;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
