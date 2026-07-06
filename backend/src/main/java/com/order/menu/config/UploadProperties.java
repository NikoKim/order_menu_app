package com.order.menu.config;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {
    private String basePath;
    private String imageUrlPrefix = "/images";
    private String publicBaseUrl;
    private List<String> allowedExtensions = List.of("jpg", "jpeg", "png", "webp");
}
