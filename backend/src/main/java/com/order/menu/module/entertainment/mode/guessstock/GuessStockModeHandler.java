package com.order.menu.module.entertainment.mode.guessstock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.module.entertainment.core.mode.GameModeHandler;
import com.order.menu.module.entertainment.mode.guessstock.config.GuessStockConfig;
import com.order.menu.module.entertainment.mode.guessstock.engine.GuessStockRules;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GuessStockModeHandler implements GameModeHandler {
    private final ObjectMapper objectMapper;

    @Override public String code() { return "GUESS_STOCK"; }
    @Override public String displayName() { return "猜股票"; }
    @Override public int configSchemaVersion() { return 1; }

    @Override
    public JsonNode validateAndNormalize(JsonNode config) {
        try {
            GuessStockConfig normalized = GuessStockRules.validate(objectMapper.treeToValue(config, GuessStockConfig.class));
            return objectMapper.valueToTree(normalized);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "猜股票配置格式错误");
        }
    }

    @Override
    public Map<String, Object> summary(JsonNode config) {
        JsonNode normalized = validateAndNormalize(config);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stockCode", normalized.path("stockCode").asText());
        result.put("stockType", normalized.path("stockType").asText());
        result.put("aiEnabled", normalized.path("aiEnabled").asBoolean());
        result.put("aiCount", normalized.path("aiCount").asInt());
        result.put("roundCount", normalized.path("roundCount").asInt());
        return result;
    }
}
