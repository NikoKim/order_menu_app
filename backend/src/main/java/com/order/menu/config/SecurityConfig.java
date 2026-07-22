package com.order.menu.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.common.response.ApiResponse;
import com.order.menu.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsProperties corsProperties;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/customer/register",
                                "/api/auth/login",
                                "/api/shops/**",
                                "/api/dishes/**",
                                "/api/version/**",
                                "/images/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/ws/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/moments/drafts",
                                "/api/moments/unread-count",
                                "/api/moments/mention-users",
                                "/api/moments/privacy",
                                "/api/moments/privacy/**",
                                "/api/moments/users/*/privacy"
                        ).authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/moments",
                                "/api/moments/*",
                                "/api/moments/*/share-card",
                                "/api/moments/*/availability",
                                "/api/moments/*/comments",
                                "/api/moments/*/comments/*",
                                "/api/moments/*/comments/*/replies",
                                "/api/moments/comments/*/replies",
                                "/api/moments/users/*/card",
                                "/api/moments/topics",
                                "/api/moments/topics/**",
                                "/e811102a0d9cad593d612a8e10ee7e4e.txt"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/moments/*/external-shares").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/shop/**").hasRole("SHOP")
                        .requestMatchers("/api/upload/shop", "/api/upload/menu", "/api/upload/family").hasRole("SHOP")
                        .requestMatchers("/api/orders/**", "/api/feedback/**", "/api/families/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/messages/**").authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> writeError(response, ErrorCode.UNAUTHORIZED, "未登录或登录已过期"))
                        .accessDeniedHandler((request, response, accessDeniedException) -> writeError(response, ErrorCode.FORBIDDEN, "无权访问该接口")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
        configuration.setAllowedMethods(corsProperties.getAllowedMethods());
        configuration.addAllowedHeader("*");
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private void writeError(jakarta.servlet.http.HttpServletResponse response, int code, String message) throws java.io.IOException {
        response.setStatus(code);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(code, message)));
    }
}
