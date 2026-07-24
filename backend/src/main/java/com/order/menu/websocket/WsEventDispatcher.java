package com.order.menu.websocket;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WsEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WsEventDispatcher.class);

    private final WsSessionRegistry registry;

    public void dispatch(WsEvent event) {
        if (event == null || event.targetType() == null || event.targetId() == null || event.payload() == null) {
            return;
        }
        try {
            WsTargetType targetType = WsTargetType.valueOf(event.targetType());
            switch (targetType) {
                case SHOP -> registry.sendToShop(event.targetId(), event.payload());
                case CUSTOMER -> registry.sendToCustomer(event.targetId(), event.payload());
                case USER -> registry.sendToUser(event.targetId(), event.payload());
            }
        } catch (IllegalArgumentException exception) {
            log.warn("Ignoring unknown WebSocket event target type: {}", event.targetType());
        }
    }
}
