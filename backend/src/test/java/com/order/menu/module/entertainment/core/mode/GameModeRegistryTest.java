package com.order.menu.module.entertainment.core.mode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.order.menu.common.exception.BusinessException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GameModeRegistryTest {
    @Test
    void registersAndLooksUpModesCaseInsensitively() {
        GameModeHandler handler = handler("TEST_MODE");
        GameModeRegistry registry = new GameModeRegistry(List.of(handler));

        assertEquals(handler, registry.require("test_mode"));
        assertEquals(List.of(handler), registry.all());
    }

    @Test
    void rejectsUnknownAndDuplicateModes() {
        assertThrows(BusinessException.class, () -> new GameModeRegistry(List.of()).require("missing"));
        assertThrows(IllegalStateException.class,
                () -> new GameModeRegistry(List.of(handler("TEST_MODE"), handler("test_mode"))));
    }

    private static GameModeHandler handler(String code) {
        return new GameModeHandler() {
            @Override public String code() { return code; }
            @Override public String displayName() { return "测试"; }
            @Override public int configSchemaVersion() { return 1; }
            @Override public JsonNode validateAndNormalize(JsonNode config) {
                return config == null ? JsonNodeFactory.instance.objectNode() : config;
            }
            @Override public Map<String, Object> summary(JsonNode config) { return Map.of(); }
        };
    }
}
