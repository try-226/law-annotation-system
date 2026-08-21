package com.law.annotation.auth.dto;

import jakarta.validation.constraints.NotNull;

public record ChangePasswordRequest(
        @NotNull(message = "旧密码不能为空") String oldPassword,
        @NotNull(message = "新密码不能为空") String newPassword,
        @NotNull(message = "确认密码不能为空") String confirmPassword) {
}
