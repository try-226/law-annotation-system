package com.law.annotation.law.dto;

import java.util.List;

public record LawImportPreviewResponse(
        LawBaseInfoInput baseInfo,
        List<LawStructureInput> structure,
        List<LawImportArticleInput> articles,
        List<String> warnings,
        List<LawValidationIssue> validationIssues) {

    public LawImportPreviewResponse {
        structure = List.copyOf(structure);
        articles = List.copyOf(articles);
        warnings = List.copyOf(warnings);
        validationIssues = List.copyOf(validationIssues);
    }
}
