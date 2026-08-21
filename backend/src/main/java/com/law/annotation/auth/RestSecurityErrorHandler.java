package com.law.annotation.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.law.annotation.common.response.ApiError;
import com.law.annotation.common.response.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

@Component
public class RestSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        writeError(
                response,
                HttpStatus.UNAUTHORIZED,
                AuthErrorCodes.UNAUTHENTICATED,
                "登录已失效，请重新登录");
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception) throws IOException, ServletException {
        if (exception instanceof CsrfException) {
            writeError(
                    response,
                    HttpStatus.FORBIDDEN,
                    AuthErrorCodes.CSRF_INVALID,
                    "请求安全校验失败");
            return;
        }
        writeError(response, HttpStatus.FORBIDDEN, AuthErrorCodes.FORBIDDEN, "无权执行此操作");
    }

    private void writeError(
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String userMessage) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiResponse.failure(new ApiError(code, userMessage, List.of())));
    }
}
