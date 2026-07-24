package com.order.menu.websocket;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LocalWsEventTransport implements WsEventTransport {

    private final WsEventDispatcher dispatcher;

    @Override
    public void publish(WsEvent event) {
        dispatcher.dispatch(event);
    }
}
