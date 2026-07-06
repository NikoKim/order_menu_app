package com.order.menu.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public final class OrderNoUtils {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private OrderNoUtils() {
    }

    public static String next() {
        return FORMATTER.format(LocalDateTime.now()) + ThreadLocalRandom.current().nextInt(100, 999);
    }
}
