package com.navy.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final CorsProperties cors;
    @Override public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(cors.getAllowedOrigins().toArray(String[]::new)).allowedMethods("GET","POST","PUT","PATCH","DELETE","OPTIONS").allowedHeaders("Content-Type","Authorization","X-Guest-Token");
    }
}
