package com.law.annotation.law.dto;

import jakarta.validation.constraints.NotNull;

public record LawImportParseRequest(
        @NotNull String fullTextPaste) {
}
