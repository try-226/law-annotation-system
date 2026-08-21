package com.law.annotation.law.dto;

import com.law.annotation.law.LawStructureNodeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

public record LawStructureInput(
        @NotBlank String nodeId,
        @NotNull LawStructureNodeType type,
        @NotBlank String title,
        String parentNodeId,
        @PositiveOrZero int order,
        List<String> articleRefs) {

    public LawStructureInput {
        articleRefs = articleRefs == null ? List.of() : List.copyOf(articleRefs);
    }
}
