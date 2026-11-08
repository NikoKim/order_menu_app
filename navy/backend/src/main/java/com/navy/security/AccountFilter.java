package com.navy.security;

import com.navy.identity.GuestService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class AccountFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        try {
            String auth=request.getHeader("Authorization");
            if(auth!=null&&auth.startsWith("Bearer ")) {
                var rows=jdbc.queryForList("SELECT u.id,u.nickname FROM account_sessions s JOIN users u ON u.id=s.user_id WHERE s.token_hash=? AND s.expires_at>?",GuestService.hash(auth.substring(7)),System.currentTimeMillis());
                if(rows.isEmpty()) {response.setStatus(401);response.setContentType("application/json;charset=UTF-8");response.getWriter().write("{\"code\":401,\"message\":\"登录已过期\",\"data\":null}");return;}
                var row=rows.get(0);UserContext.set(new LoginUser(((Number)row.get("id")).longValue(),(String)row.get("nickname")));
            }
            chain.doFilter(request,response);
        } finally {UserContext.clear();}
    }
}
