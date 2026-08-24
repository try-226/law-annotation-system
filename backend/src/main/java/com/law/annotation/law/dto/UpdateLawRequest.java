package com.law.annotation.law.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateLawRequest(
        @Valid @NotNull LawBaseInfoInput baseInfo,
        @NotNull List<@Valid LawStructureInput> structure,
        @Size(min = 1) @NotNull List<@Valid UpdateLawArticleInput> articles) {

    public UpdateLawRequest {
        structure = structure == null ? null : List.copyOf(structure);
        articles = articles == null ? null : List.copyOf(articles);
    }
}
