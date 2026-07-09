package com.order.menu.websocket;

import com.order.menu.module.user.entity.User;
import com.order.menu.module.user.mapper.UserMapper;
import com.order.menu.security.JwtTokenProvider;
import com.order.menu.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        try {
            String token = getToken(request);
            if (token == null || token.isBlank()) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            LoginUser loginUser = jwtTokenProvider.parse(token);
            User user = userMapper.selectById(loginUser.userId());

            if (user == null || user.getStatus() != 1) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            String path = request.getURI().getPath();
            String role = user.getRole();

            if (path.endsWith("/ws/shop") && !"SHOP".equals(role)) {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }

            if (path.endsWith("/ws/customer") && !"CUSTOMER".equals(role)) {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }

            attributes.put("userId", user.getId());
            attributes.put("role", role);
            attributes.put("nickname", user.getNickname());

            return true;
        } catch (Exception e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    private String getToken(ServerHttpRequest request) {
        List<String> queryTokens = UriComponentsBuilder
                .fromUri(request.getURI())
                .build()
                .getQueryParams()
                .get("token");

        if (queryTokens != null && !queryTokens.isEmpty()) {
            return queryTokens.get(0);
        }

        List<String> authHeaders = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String header = authHeaders.get(0);
            if (header.startsWith("Bearer ")) {
                return header.substring("Bearer ".length());
            }
        }

        return null;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
    }
}
