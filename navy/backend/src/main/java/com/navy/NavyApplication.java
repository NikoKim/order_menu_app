package com.navy;

import com.navy.config.CorsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(CorsProperties.class)
public class NavyApplication {
    public static void main(String[] args) { SpringApplication.run(NavyApplication.class, args); }
}
