package com.order.menu.common.ratelimit;

import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.config.RedisRealtimeProperties;
import com.order.menu.module.auth.dto.AuthDtos;
import com.order.menu.security.LoginUser;
import com.order.menu.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.util.DigestUtils;

@Aspect
@RequiredArgsConstructor
public class CriticalApiRateLimitAspect {

    private final RateLimiter rateLimiter;
    private final RedisRealtimeProperties properties;

    @Around("execution(* com.order.menu.module.auth.controller.AuthController.login(..))")
    public Object limitLogin(ProceedingJoinPoint joinPoint) throws Throwable {
        String account = loginAccount(joinPoint);
        requirePermit(
                "login-source",
                requestAddress() + ":" + account,
                properties.getLoginLimit(),
                properties.getLoginWindowSeconds());
        requirePermit(
                "login-account",
                account,
                properties.getLoginAccountLimit(),
                properties.getLoginAccountWindowSeconds());
        return joinPoint.proceed();
    }

    @Around("execution(* com.order.menu.module.order.controller.OrderController.create(..))")
    public Object limitOrderCreate(ProceedingJoinPoint joinPoint) throws Throwable {
        return guard(
                joinPoint,
                "order-create",
                currentUserSubject(),
                properties.getOrderCreateLimit(),
                properties.getOrderCreateWindowSeconds());
    }

    @Around("execution(* com.order.menu.module.message.controller.MessageController.sendMessage(..)) || "
            + "execution(* com.order.menu.module.message.controller.MessageController.sendImage(..))")
    public Object limitMessageSend(ProceedingJoinPoint joinPoint) throws Throwable {
        return guard(
                joinPoint,
                "message-send",
                currentUserSubject(),
                properties.getMessageSendLimit(),
                properties.getMessageSendWindowSeconds());
    }

    @Around("execution(* com.order.menu.module.moment.controller.MomentController.comment(..)) && "
            + "@annotation(org.springframework.web.bind.annotation.PostMapping)")
    public Object limitMomentComment(ProceedingJoinPoint joinPoint) throws Throwable {
        return guard(
                joinPoint,
                "moment-comment",
                currentUserSubject(),
                properties.getMomentCommentLimit(),
                properties.getMomentCommentWindowSeconds());
    }

    private Object guard(
            ProceedingJoinPoint joinPoint,
            String action,
            String subject,
            int limit,
            long windowSeconds) throws Throwable {
        requirePermit(action, subject, limit, windowSeconds);
        return joinPoint.proceed();
    }

    private void requirePermit(String action, String subject, int limit, long windowSeconds) {
        if (!rateLimiter.tryAcquire(action, subject, limit, Duration.ofSeconds(windowSeconds))) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
        }
    }

    private String loginAccount(ProceedingJoinPoint joinPoint) {
        for (Object argument : joinPoint.getArgs()) {
            if (argument instanceof AuthDtos.LoginRequest request) {
                String canonical = (request.role() + ":" + request.nickname().trim())
                        .toLowerCase(Locale.ROOT);
                return DigestUtils.md5DigestAsHex(canonical.getBytes(StandardCharsets.UTF_8));
            }
        }
        return "unknown";
    }

    private String currentUserSubject() {
        LoginUser user = UserContext.get();
        return "user-" + user.userId();
    }

    private String requestAddress() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return "ip-" + request.getRemoteAddr();
        }
        return "ip-unknown";
    }
}
