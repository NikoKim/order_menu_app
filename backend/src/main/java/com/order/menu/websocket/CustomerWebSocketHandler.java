package com.order.menu.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;


@Component
@RequiredArgsConstructor
public class CustomerWebSocketHandler extends TextWebSocketHandler {
    private final WsSessionRegistry registry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long customerUserId = (Long) session.getAttributes().get("userId");

        if (customerUserId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("missing userId"));
            return;
        }

        session.getAttributes().put("customerUserId", customerUserId);

        registry.registerCustomer(customerUserId, session);
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
