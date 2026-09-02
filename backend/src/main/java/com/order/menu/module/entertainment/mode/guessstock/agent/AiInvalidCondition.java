package com.order.menu.module.entertainment.mode.guessstock.agent;

import java.math.BigDecimal;

public record AiInvalidCondition(
        String type,
        BigDecimal value,
        Integer windowRounds,
        BigDecimal valuePct,
        String direction,
        String severity,
        String eventType
) {}
