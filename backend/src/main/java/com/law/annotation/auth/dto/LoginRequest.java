package com.law.annotation.auth.dto;

import com.law.annotation.common.enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginRequest(
        @NotBlank(message = "登录账号不能为空") String loginAccount,
        @NotBlank(message = "密码不能为空") String password,
        @NotNull(message = "预期角色不能为空") Role expectedRole) {
}
