package com.law.annotation.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequest(@NotBlank(message = "姓名不能为空") String name) {
}
