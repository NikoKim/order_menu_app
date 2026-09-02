package com.order.menu.module.entertainment.core.mode;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public interface GameModeHandler {
    String code();
    String displayName();
    int configSchemaVersion();
    JsonNode validateAndNormalize(JsonNode config);
    Map<String, Object> summary(JsonNode config);
}
