package com.order.menu.module.entertainment.mode.naval;
import com.order.menu.config.CorsProperties;
import com.order.menu.module.entertainment.mode.naval.PageSocket;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;
@Configuration @EnableWebSocket @RequiredArgsConstructor
public class NavalSocketConfig implements WebSocketConfigurer {
    private final PageSocket socket;private final CorsProperties cors;
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry){registry.addHandler(socket,"/api/entertainment/naval/live").setAllowedOrigins(cors.getAllowedOrigins().toArray(String[]::new));}
}
