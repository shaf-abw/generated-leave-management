package com.example.leave.common;

import java.util.List;

public record ApiError(int status, String error, String message, List<FieldErrorDetail> fieldErrors) {

    public record FieldErrorDetail(String field, String message) {
    }
}
