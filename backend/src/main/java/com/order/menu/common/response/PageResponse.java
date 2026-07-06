package com.order.menu.common.response;

import java.util.List;

public record PageResponse<T>(List<T> records, long page, long pageSize, long total) {
}
