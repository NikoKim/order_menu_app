package com.order.menu.security;

import com.order.menu.config.JwtProperties;
import com.order.menu.module.user.entity.User;
import com.order.menu.module.user.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtProperties jwtProperties;
    private final JwtTokenProvider tokenProvider;
    private final UserMapper userMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader(jwtProperties.getHeader());
            String prefix = jwtProperties.getTokenPrefix() + " ";
            if (header != null && header.startsWith(prefix)) {
                LoginUser tokenUser = tokenProvider.parse(header.substring(prefix.length()));
                User user = userMapper.selectById(tokenUser.userId());
                if (user != null && user.getStatus() == 1) {
                    LoginUser loginUser = new LoginUser(user.getId(), user.getNickname(), user.getRole());
                    UserContext.set(loginUser);
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            loginUser,
                            null,
                            java.util.List.of(new SimpleGrantedAuthority("ROLE_" + loginUser.role()))
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
