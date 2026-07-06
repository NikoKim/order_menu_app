package com.order.menu.common.constant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StatusConstantsTest {
    @Test
    void recognizesAllowedStatuses() {
        assertTrue(StatusConstants.isBusinessStatus("OPEN"));
        assertTrue(StatusConstants.isBusinessStatus("CLOSED"));
        assertTrue(StatusConstants.isDishStatus("ON_SALE"));
        assertTrue(StatusConstants.isDishStatus("OFF_SALE"));
        assertTrue(StatusConstants.isOrderStatus("SUBMITTED"));
        assertTrue(StatusConstants.isOrderStatus("ACCEPTED"));
        assertTrue(StatusConstants.isOrderStatus("COMPLETED"));
        assertTrue(StatusConstants.isOrderStatus("CANCELLED"));
        assertTrue(StatusConstants.isFeedbackStatus("PENDING"));
        assertTrue(StatusConstants.isFeedbackStatus("REPLIED"));
        assertTrue(StatusConstants.isFeedbackStatus("CLOSED"));
    }

    @Test
    void rejectsUnknownStatuses() {
        assertFalse(StatusConstants.isBusinessStatus("PAUSED"));
        assertFalse(StatusConstants.isDishStatus("SOLD_OUT"));
        assertFalse(StatusConstants.isOrderStatus("PAID"));
        assertFalse(StatusConstants.isFeedbackStatus("DELETED"));
    }
}
