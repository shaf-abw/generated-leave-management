package com.example.leave.common;

import java.util.List;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final List<ApiError.FieldErrorDetail> fieldErrors;

    public ApiException(HttpStatus status, String message) {
        this(status, message, List.of());
    }

    public ApiException(HttpStatus status, String message, List<ApiError.FieldErrorDetail> fieldErrors) {
        super(message);
        this.status = status;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }

    public static ApiException invalidField(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Validation failed",
                List.of(new ApiError.FieldErrorDetail(field, message)));
    }

    public HttpStatus getStatus() {
        return status;
    }

    public List<ApiError.FieldErrorDetail> getFieldErrors() {
        return fieldErrors;
    }
}
