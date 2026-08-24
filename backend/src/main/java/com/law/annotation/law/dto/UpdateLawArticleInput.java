package com.law.annotation.law.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateLawArticleInput(
        String articleId,
        @NotBlank String clientKey,
        @NotNull String number,
        @NotNull String body,
        @PositiveOrZero int order) {
}
