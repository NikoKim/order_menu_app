package com.order.menu.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PageUtilsTest {
    @Test
    void normalizesPageToAtLeastOne() {
        assertEquals(1, PageUtils.page(-3));
        assertEquals(1, PageUtils.page(0));
        assertEquals(2, PageUtils.page(2));
    }

    @Test
    void normalizesPageSizeToDefaultAndMaximum() {
        assertEquals(10, PageUtils.pageSize(0));
        assertEquals(10, PageUtils.pageSize(-1));
        assertEquals(20, PageUtils.pageSize(20));
        assertEquals(100, PageUtils.pageSize(101));
    }
}
