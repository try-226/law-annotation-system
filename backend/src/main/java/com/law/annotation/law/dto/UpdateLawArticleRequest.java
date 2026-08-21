package com.law.annotation.law.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateLawArticleRequest(
        @NotNull String number,
        @NotNull String body,
        @PositiveOrZero int order) {
}
