package com.law.annotation.common.response;

import java.util.Objects;

public record ErrorLocator(String path, String message) {

    public ErrorLocator {
        path = requireText(path, "path");
        message = requireText(message, "message");
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
