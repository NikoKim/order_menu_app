package com.order.menu.common.constant;

import java.util.Set;

public final class StatusConstants {
    public static final String BUSINESS_OPEN = "OPEN";
    public static final String BUSINESS_CLOSED = "CLOSED";

    public static final String DISH_ON_SALE = "ON_SALE";
    public static final String DISH_OFF_SALE = "OFF_SALE";
    public static final String DISH_DELETE = "DELETED";

    public static final String ORDER_SUBMITTED = "SUBMITTED";
    public static final String ORDER_ACCEPTED = "ACCEPTED";
    public static final String ORDER_COMPLETED = "COMPLETED";
    public static final String ORDER_CANCELLED = "CANCELLED";
    public static final String ORDER_REJECTED = "REJECTED";

    public static final String FEEDBACK_PENDING = "PENDING";
    public static final String FEEDBACK_REPLIED = "REPLIED";
    public static final String FEEDBACK_CLOSED = "CLOSED";

    private static final Set<String> BUSINESS_STATUSES = Set.of(BUSINESS_OPEN, BUSINESS_CLOSED);
    private static final Set<String> DISH_STATUSES = Set.of(DISH_ON_SALE, DISH_OFF_SALE);
    private static final Set<String> ORDER_STATUSES = Set.of(ORDER_SUBMITTED, ORDER_ACCEPTED, ORDER_COMPLETED, ORDER_CANCELLED, ORDER_REJECTED);
    private static final Set<String> FEEDBACK_STATUSES = Set.of(FEEDBACK_PENDING, FEEDBACK_REPLIED, FEEDBACK_CLOSED);

    private StatusConstants() {
    }

    public static boolean isBusinessStatus(String status) {
        return BUSINESS_STATUSES.contains(status);
    }

    public static boolean isDishStatus(String status) {
        return DISH_STATUSES.contains(status);
    }

    public static boolean isOrderStatus(String status) {
        return ORDER_STATUSES.contains(status);
    }

    public static boolean isFeedbackStatus(String status) {
        return FEEDBACK_STATUSES.contains(status);
    }
}
