package com.order.menu.common.util;

public final class PageUtils {
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;

    private PageUtils() {
    }

    public static int page(int page) {
        return Math.max(page, DEFAULT_PAGE);
    }

    public static int pageSize(int pageSize) {
        if (pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
