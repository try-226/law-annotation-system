package com.law.annotation.user.dto;

import com.law.annotation.common.enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateUserRequest(
        @NotBlank(message = "姓名不能为空") String name,
        @NotBlank(message = "登录账号不能为空") String loginAccount,
        @NotBlank(message = "初始密码不能为空") String initialPassword,
        @NotNull(message = "角色不能为空") Role role) {
}
