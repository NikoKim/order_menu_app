package com.order.menu.common.ratelimit;

import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.GlobalExceptionHandler;
import com.order.menu.config.RedisRealtimeProperties;
import com.order.menu.module.auth.dto.AuthDtos;
import com.order.menu.security.LoginUser;
import com.order.menu.security.UserContext;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CriticalApiRateLimitAspectTest {

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void loginUsesSeparateAccountAndSourceAccountQuotas() throws Throwable {
        List<String> calls = new ArrayList<>();
        RateLimiter limiter = (action, subject, limit, window) -> {
            calls.add(action + ":" + subject + ":" + limit + ":" + window.toSeconds());
            return true;
        };
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{
                new AuthDtos.LoginRequest("Alice", "secret", "CUSTOMER")
        });
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = new CriticalApiRateLimitAspect(limiter, new RedisRealtimeProperties())
                .limitLogin(joinPoint);

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasSize(2);
        assertThat(calls.get(0)).startsWith("login-source:ip-unknown:").endsWith(":10:60");
        assertThat(calls.get(1)).startsWith("login-account:").endsWith(":20:300");
        verify(joinPoint).proceed();
    }

    @Test
    void permitsRequestAndUsesUserScopedOrderKey() throws Throwable {
        AtomicReference<String> call = new AtomicReference<>();
        RateLimiter limiter = (action, subject, limit, window) -> {
            call.set(action + ":" + subject + ":" + limit + ":" + window.toSeconds());
            return true;
        };
        RedisRealtimeProperties properties = new RedisRealtimeProperties();
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("ok");
        UserContext.set(new LoginUser(7L, "tester", "CUSTOMER"));

        Object result = new CriticalApiRateLimitAspect(limiter, properties)
                .limitOrderCreate(joinPoint);

        assertThat(result).isEqualTo("ok");
        assertThat(call).hasValue("order-create:user-7:5:60");
        verify(joinPoint).proceed();
    }

    @Test
    void rejectsRequestBeforeControllerWhenQuotaIsExhausted() {
        RateLimiter limiter = (action, subject, limit, window) -> false;
        RedisRealtimeProperties properties = new RedisRealtimeProperties();
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        UserContext.set(new LoginUser(8L, "tester", "CUSTOMER"));

        assertThatThrownBy(() -> new CriticalApiRateLimitAspect(limiter, properties)
                .limitMessageSend(joinPoint))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(429);
    }

    @Test
    void rateLimitBusinessCodeMapsToHttp429() {
        var response = new GlobalExceptionHandler()
                .handleBusiness(new BusinessException(429, "limited"));

        assertThat(response.getStatusCode().value()).isEqualTo(429);
    }
}
