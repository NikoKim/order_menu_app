package com.order.menu.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({UploadProperties.class, JwtProperties.class, CorsProperties.class})
public class WebMvcConfig implements WebMvcConfigurer {
    private final UploadProperties uploadProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String basePath = uploadProperties.getBasePath();
        if (basePath != null && !basePath.isBlank()) {
            registry.addResourceHandler(uploadProperties.getImageUrlPrefix() + "/**")
                    .addResourceLocations("file:" + (basePath.endsWith("/") ? basePath : basePath + "/"));
        }
    }
}
