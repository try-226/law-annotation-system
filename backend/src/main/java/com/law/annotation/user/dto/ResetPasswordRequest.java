package com.law.annotation.user.dto;

import jakarta.validation.constraints.NotNull;

public record ResetPasswordRequest(
        @NotNull(message = "新密码不能为空") String newPassword,
        @NotNull(message = "确认密码不能为空") String confirmPassword) {
}
