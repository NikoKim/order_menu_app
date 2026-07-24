package com.order.menu.websocket;

import java.time.Instant;
import java.util.UUID;

public record WsEvent(
        String eventId,
        String targetType,
        Long targetId,
        WsMessage payload,
        Instant occurredAt,
        String sourceInstanceId
) {
    public static WsEvent create(WsTargetType targetType, Long targetId, WsMessage payload,
                                 String sourceInstanceId) {
        return new WsEvent(UUID.randomUUID().toString(), targetType.name(), targetId, payload,
                Instant.now(), sourceInstanceId);
    }
}
