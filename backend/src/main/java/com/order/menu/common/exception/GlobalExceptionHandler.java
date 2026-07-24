package com.order.menu.common.exception;

import com.order.menu.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception) {
        return ResponseEntity.status(httpStatus(exception.getCode()))
                .body(ApiResponse.fail(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<Void>> handleBind(Exception exception) {
        String message = "Invalid request parameters";
        if (exception instanceof MethodArgumentNotValidException ex && ex.getBindingResult().hasFieldErrors()) {
            message = ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        } else if (exception instanceof BindException ex && ex.getBindingResult().hasFieldErrors()) {
            message = ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        }
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.BAD_REQUEST, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.BAD_REQUEST, exception.getMessage()));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            MultipartException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest() {
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.BAD_REQUEST, "Invalid request parameters"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadSize() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.fail(ErrorCode.PAYLOAD_TOO_LARGE, "Uploaded file is too large"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType() {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.fail(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.fail(405, "Method not allowed"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception exception) {
        log.error("Unhandled exception", exception);
        return ResponseEntity.internalServerError().body(ApiResponse.fail(ErrorCode.SERVER_ERROR, "Server error"));
    }

    private HttpStatus httpStatus(int code) {
        return switch (code) {
            case ErrorCode.BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case ErrorCode.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case ErrorCode.FORBIDDEN -> HttpStatus.FORBIDDEN;
            case ErrorCode.NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ErrorCode.CONFLICT -> HttpStatus.CONFLICT;
            case ErrorCode.PAYLOAD_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case ErrorCode.UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case ErrorCode.TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
