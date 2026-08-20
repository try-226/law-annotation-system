package com.law.annotation.common.response;

import java.time.Instant;
import java.util.Objects;

public record ApiResponse<T>(boolean success, T data, ApiError error, Instant timestamp) {

    public ApiResponse {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        if (success && error != null) {
            throw new IllegalArgumentException("A successful response must not contain an error");
        }
        if (!success && error == null) {
            throw new IllegalArgumentException("A failed response must contain an error");
        }
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> failure(ApiError error) {
        return new ApiResponse<>(false, null, Objects.requireNonNull(error), Instant.now());
    }
}
