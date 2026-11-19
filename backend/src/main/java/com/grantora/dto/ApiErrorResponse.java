package com.grantora.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        int status,
        String error,
        String message,
        Map<String, String> errors,
        String path,
        Instant timestamp
) {
    public ApiErrorResponse(int status, String error, String message, String path) {
        this(status, error, message, null, path, Instant.now());
    }

    public ApiErrorResponse(int status, String error, String message, Map<String, String> errors, String path) {
        this(status, error, message, errors, path, Instant.now());
    }
}
