package com.order.menu.websocket;

public interface WsEventTransport {
    void publish(WsEvent event);
}
