package com.order.menu.common.exception;

public final class ErrorCode {
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int CONFLICT = 409;
    public static final int PAYLOAD_TOO_LARGE = 413;
    public static final int UNSUPPORTED_MEDIA_TYPE = 415;
    public static final int TOO_MANY_REQUESTS = 429;
    public static final int SERVER_ERROR = 500;

    private ErrorCode() {
    }
}
