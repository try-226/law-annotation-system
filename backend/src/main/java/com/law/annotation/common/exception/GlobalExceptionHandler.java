package com.law.annotation.common.exception;

import com.law.annotation.common.response.ApiError;
import com.law.annotation.common.response.ApiResponse;
import com.law.annotation.common.response.ErrorLocator;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String VALIDATION_FAILED = "COMMON.VALIDATION_FAILED";
    private static final String MALFORMED_REQUEST = "COMMON.MALFORMED_REQUEST";
    private static final String INTERNAL_ERROR = "COMMON.INTERNAL_ERROR";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception) {
        List<ErrorLocator> locators = exception.getBindingResult().getAllErrors().stream()
                .map(error -> {
                    String path = error instanceof FieldError fieldError
                            ? fieldError.getField()
                            : error.getObjectName();
                    return new ErrorLocator(path, messageOf(error));
                })
                .toList();
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                VALIDATION_FAILED,
                "请求参数校验失败",
                locators);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidation(
            HandlerMethodValidationException exception) {
        Stream<ErrorLocator> parameterLocators = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ErrorLocator(
                                parameterPath(result.getMethodParameter().getParameterName()),
                                messageOf(error))));
        Stream<ErrorLocator> crossParameterLocators = exception.getCrossParameterValidationResults().stream()
                .map(error -> new ErrorLocator("request", messageOf(error)));
        List<ErrorLocator> locators = Stream.concat(parameterLocators, crossParameterLocators)
                .toList();
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                VALIDATION_FAILED,
                "请求参数校验失败",
                locators);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException exception) {
        List<ErrorLocator> locators = exception.getConstraintViolations().stream()
                .map(violation -> new ErrorLocator(
                        lastPathSegment(violation.getPropertyPath().toString()),
                        violation.getMessage()))
                .toList();
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                VALIDATION_FAILED,
                "请求参数校验失败",
                locators);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(
            HttpMessageNotReadableException exception) {
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                MALFORMED_REQUEST,
                "请求内容格式错误",
                List.of());
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception) {
        return errorResponse(
                exception.getStatus(),
                exception.getCode(),
                exception.getUserMessage(),
                exception.getLocators());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        LOGGER.error("Unhandled exception", exception);
        return errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                INTERNAL_ERROR,
                "服务器内部错误",
                List.of());
    }

    private ResponseEntity<ApiResponse<Void>> errorResponse(
            HttpStatus status,
            String code,
            String userMessage,
            List<ErrorLocator> locators) {
        ApiError error = new ApiError(code, userMessage, locators);
        return ResponseEntity.status(status).body(ApiResponse.failure(error));
    }

    private static String messageOf(MessageSourceResolvable error) {
        String defaultMessage = error.getDefaultMessage();
        return defaultMessage == null || defaultMessage.isBlank()
                ? "参数值无效"
                : defaultMessage;
    }

    private static String parameterPath(String parameterName) {
        return parameterName == null || parameterName.isBlank()
                ? "request"
                : parameterName;
    }

    private static String lastPathSegment(String path) {
        if (path == null || path.isBlank()) {
            return "request";
        }
        int separatorIndex = path.lastIndexOf('.');
        return separatorIndex < 0 ? path : path.substring(separatorIndex + 1);
    }
}
