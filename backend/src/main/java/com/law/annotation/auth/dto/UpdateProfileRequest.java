package com.law.annotation.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(@NotBlank(message = "姓名不能为空") String name) {
}
