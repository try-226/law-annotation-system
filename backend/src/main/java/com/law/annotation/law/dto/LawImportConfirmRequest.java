package com.law.annotation.law.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record LawImportConfirmRequest(
        @NotNull @Valid LawBaseInfoInput baseInfo,
        @NotNull List<@Valid LawStructureInput> structure,
        @NotNull @Size(min = 1) List<@Valid LawImportArticleInput> articles) {

    public LawImportConfirmRequest {
        structure = structure == null ? null : List.copyOf(structure);
        articles = articles == null ? null : List.copyOf(articles);
    }
}
