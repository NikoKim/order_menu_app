package com.order.menu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@MapperScan({
        "com.order.menu.module.*.mapper",
        "com.order.menu.module.entertainment.core.mapper",
        "com.order.menu.module.entertainment.mode.guessstock.mapper",
        "com.order.menu.module.entertainment.mode.guessstock.credit.mapper",
        "com.order.menu.module.entertainment.mode.guessstock.challenge"
})
public class OrderMenuApplication {
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        SpringApplication.run(OrderMenuApplication.class, args);
    }
}
