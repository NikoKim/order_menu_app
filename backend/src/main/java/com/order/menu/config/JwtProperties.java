package com.order.menu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
    private String header = "Authorization";
    private String tokenPrefix = "Bearer";
    private long expireSeconds = 604800;
    private String secret;
}
