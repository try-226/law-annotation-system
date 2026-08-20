package com.law.annotation.common.response;

import java.util.List;
import java.util.Objects;

public record ApiError(String code, String userMessage, List<ErrorLocator> locators) {

    public ApiError {
        code = requireText(code, "code");
        userMessage = requireText(userMessage, "userMessage");
        locators = locators == null ? List.of() : List.copyOf(locators);
    }

    public static ApiError of(String code, String userMessage) {
        return new ApiError(code, userMessage, List.of());
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
