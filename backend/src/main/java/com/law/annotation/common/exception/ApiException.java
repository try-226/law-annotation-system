package com.law.annotation.common.exception;

import com.law.annotation.common.response.ErrorLocator;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String userMessage;
    private final List<ErrorLocator> locators;

    public ApiException(HttpStatus status, String code, String userMessage) {
        this(status, code, userMessage, List.of());
    }

    public ApiException(
            HttpStatus status,
            String code,
            String userMessage,
            List<ErrorLocator> locators) {
        super(userMessage);
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.code = requireText(code, "code");
        this.userMessage = requireText(userMessage, "userMessage");
        this.locators = locators == null ? List.of() : List.copyOf(locators);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public List<ErrorLocator> getLocators() {
        return locators;
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
