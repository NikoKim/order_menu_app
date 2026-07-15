package com.order.menu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@MapperScan("com.order.menu.module.*.mapper")
public class OrderMenuApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderMenuApplication.class, args);
    }
}
