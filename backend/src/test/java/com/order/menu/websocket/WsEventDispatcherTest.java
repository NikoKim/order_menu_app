package com.order.menu.websocket;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class WsEventDispatcherTest {

    @Test
    void routesUserEventToLocalRegistry() {
        WsSessionRegistry registry = mock(WsSessionRegistry.class);
        WsMessage payload = WsMessage.of("CHAT", "UPDATED", Map.of());
        WsEvent event = new WsEvent("event-1", "USER", 8L, payload, Instant.now(), "instance-a");

        new WsEventDispatcher(registry).dispatch(event);

        verify(registry).sendToUser(8L, payload);
    }

    @Test
    void ignoresUnknownTargetTypeForForwardCompatibility() {
        WsSessionRegistry registry = mock(WsSessionRegistry.class);
        WsEvent event = new WsEvent("event-1", "FUTURE_TARGET", 8L,
                WsMessage.of("CHAT", "UPDATED", Map.of()), Instant.now(), "instance-a");

        new WsEventDispatcher(registry).dispatch(event);

        verify(registry, never()).sendToUser(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(registry, never()).sendToShop(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(registry, never()).sendToCustomer(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
