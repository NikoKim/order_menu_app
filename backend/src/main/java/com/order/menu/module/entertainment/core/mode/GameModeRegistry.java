package com.order.menu.module.entertainment.core.mode;

import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GameModeRegistry {
    private final Map<String, GameModeHandler> handlers;

    public GameModeRegistry(List<GameModeHandler> handlers) {
        Map<String, GameModeHandler> registered = new LinkedHashMap<>();
        handlers.stream().sorted(java.util.Comparator.comparing(GameModeHandler::code)).forEach(handler -> {
            String code = handler.code().toUpperCase(Locale.ROOT);
            if (registered.putIfAbsent(code, handler) != null) {
                throw new IllegalStateException("Duplicate entertainment mode: " + code);
            }
        });
        this.handlers = Map.copyOf(registered);
    }

    public GameModeHandler require(String code) {
        GameModeHandler handler = handlers.get(code == null ? "" : code.toUpperCase(Locale.ROOT));
        if (handler == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的游戏模式");
        return handler;
    }

    public List<GameModeHandler> all() {
        return handlers.values().stream().toList();
    }
}
