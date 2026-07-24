package com.order.menu.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.menu.common.redis.RedisJsonCodec;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WsEventSerializationTest {

    @Test
    void roundTripsDedicatedEventDto() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        RedisJsonCodec codec = new RedisJsonCodec(objectMapper);
        WsEvent original = WsEvent.create(WsTargetType.CUSTOMER, 17L,
                WsMessage.of("ORDER", "COMPLETED", Map.of("orderId", 91L)), "instance-a");

        WsEvent decoded = codec.read(codec.write(original), WsEvent.class);

        assertThat(decoded.eventId()).isEqualTo(original.eventId());
        assertThat(decoded.targetType()).isEqualTo("CUSTOMER");
        assertThat(decoded.targetId()).isEqualTo(17L);
        assertThat(decoded.payload().module()).isEqualTo("ORDER");
        assertThat(decoded.payload().data()).containsEntry("orderId", 91);
        assertThat(decoded.occurredAt()).isEqualTo(original.occurredAt());
    }
}
