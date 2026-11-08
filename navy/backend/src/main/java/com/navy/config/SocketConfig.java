package com.navy.config;
import com.navy.game.PageSocket;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;
@Configuration @EnableWebSocket @RequiredArgsConstructor
public class SocketConfig implements WebSocketConfigurer {
    private final PageSocket socket;private final CorsProperties cors;
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry){registry.addHandler(socket,"/api/naval/live").setAllowedOrigins(cors.getAllowedOrigins().toArray(String[]::new));}
}
