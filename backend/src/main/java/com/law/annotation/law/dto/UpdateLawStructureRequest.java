package com.law.annotation.law.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateLawStructureRequest(
        @NotNull List<@Valid LawStructureInput> structure) {

    public UpdateLawStructureRequest {
        structure = structure == null ? null : List.copyOf(structure);
    }
}
