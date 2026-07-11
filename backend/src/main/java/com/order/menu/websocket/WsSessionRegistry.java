package com.order.menu.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class WsSessionRegistry {
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<Long, Set<WebSocketSession>> shopSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> customerSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    public void registerShop(Long shopId, WebSocketSession session) {
        shopSessions
                .computeIfAbsent(shopId, key -> ConcurrentHashMap.newKeySet())
                .add(session);
    }

    public void registerCustomer(Long customerId, WebSocketSession session) {
        customerSessions
                .computeIfAbsent(customerId, key -> ConcurrentHashMap.newKeySet())
                .add(session);
    }

    /** Registers either account role by its user identity for chat and notifications. */
    public void registerUser(Long userId, WebSocketSession session) {
        userSessions
                .computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet())
                .add(session);
    }

    public void unregister(WebSocketSession session) {
        shopSessions.values().forEach(set -> set.remove(session));
        customerSessions.values().forEach(set -> set.remove(session));
        userSessions.values().forEach(set -> set.remove(session));
    }

    public void sendToShop(Long shopId, WsMessage message) {
        send(shopSessions.get(shopId), message);
    }

    public void sendToCustomer(Long customerId, WsMessage message) {
        send(customerSessions.get(customerId), message);
    }

    public void sendToUser(Long userId, WsMessage message) {
        send(userSessions.get(userId), message);
    }

    private void send(Set<WebSocketSession> sessions, WsMessage message) {
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            return;
        }

        Iterator<WebSocketSession> iterator = sessions.iterator();
        while (iterator.hasNext()) {
            WebSocketSession session = iterator.next();

            if (!session.isOpen()) {
                iterator.remove();
                continue;
            }

            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(payload));
                }
            } catch (Exception e) {
                iterator.remove();
            }
        }
    }
}
