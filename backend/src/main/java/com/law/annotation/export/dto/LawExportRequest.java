package com.law.annotation.export.dto;

import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record LawExportRequest(
        @NotNull(message = "scope不能为空") Scope scope,
        List<String> articleIds,
        @NotNull(message = "type不能为空") Type type,
        @NotNull(message = "format不能为空") Format format) {

    public LawExportRequest {
        articleIds = articleIds == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(articleIds));
    }

    public enum Scope {
        WHOLE,
        SELECTED
    }

    public enum Type {
        PLAIN,
        FORMAL
    }

    public enum Format {
        CSV,
        JSON
    }
}
