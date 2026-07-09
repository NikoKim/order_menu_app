package com.order.menu.config;

import com.order.menu.websocket.CustomerWebSocketHandler;
import com.order.menu.websocket.JwtHandshakeInterceptor;
import com.order.menu.websocket.ShopWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ShopWebSocketHandler shopWebSocketHandler;
    private final CustomerWebSocketHandler customerWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    public WebSocketConfig(ShopWebSocketHandler shopWebSocketHandler, CustomerWebSocketHandler customerWebSocketHandler, JwtHandshakeInterceptor jwtHandshakeInterceptor) {
        this.shopWebSocketHandler = shopWebSocketHandler;
        this.customerWebSocketHandler = customerWebSocketHandler;
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler((WebSocketHandler) shopWebSocketHandler, "/ws/shop")
                .addInterceptors((HandshakeInterceptor) jwtHandshakeInterceptor)
                .setAllowedOriginPatterns("*");

        // 先预留顾客端 WS，不影响当前商家端改造
        registry.addHandler((WebSocketHandler) customerWebSocketHandler, "/ws/customer")
                .addInterceptors((HandshakeInterceptor) jwtHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}