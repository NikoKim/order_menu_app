package com.order.menu.websocket;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.order.menu.module.shop.entity.Shop;
import com.order.menu.module.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@RequiredArgsConstructor
public class ShopWebSocketHandler extends TextWebSocketHandler {
    private final WsSessionRegistry registry;
    private final ShopService shopService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long ownerUserId = (Long) session.getAttributes().get("userId");

        if (ownerUserId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("missing userId"));
            return;
        }

        Shop shop;

        try {
            shop = shopService.requireOwnedShop(ownerUserId);
        } catch (Exception e) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("shop not found"));
            return;
        }

        session.getAttributes().put("ownerUserId", ownerUserId);
        session.getAttributes().put("shopId", shop.getId());

        registry.registerShop(shop.getId(), session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        if (payload != null && payload.contains("PING")) {
            session.sendMessage(new TextMessage("""
                    {"module":"SYSTEM","action":"PONG","data":{}}
                    """));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        registry.unregister(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        registry.unregister(session);

        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }
}
