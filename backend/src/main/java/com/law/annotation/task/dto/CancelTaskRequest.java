package com.law.annotation.task.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelTaskRequest(
        @NotBlank(message = "取消原因不能为空") String reason) {
}
