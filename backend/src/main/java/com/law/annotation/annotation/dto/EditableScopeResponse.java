package com.law.annotation.annotation.dto;

import java.util.List;

public record EditableScopeResponse(
        boolean overallEditable,
        List<String> editableArticleIds) {

    public EditableScopeResponse {
        editableArticleIds = List.copyOf(editableArticleIds);
    }
}
