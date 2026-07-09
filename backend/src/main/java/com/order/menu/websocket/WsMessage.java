package com.order.menu.websocket;

import java.time.Instant;
import java.util.Map;

public record WsMessage(
        String module,
        String action,
        Map<String, Object> data,
        long timestamp
) {
    public static WsMessage of(String module, String action, Map<String, Object> data) {
        return new WsMessage(module, action, data, Instant.now().toEpochMilli());
    }
}
