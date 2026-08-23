package com.law.annotation.annotation.dto;

import com.law.annotation.annotation.OverallAnnotationFields;
import java.time.Instant;
import java.util.List;

public record AnnotationDraftResponse(
        String taskId,
        String annotatorId,
        OverallAnnotationFields overallFields,
        boolean overallFilled,
        List<ArticleDraftResponse> articleFields,
        int filledArticleCount,
        int totalArticleCount,
        Instant createdAt,
        Instant updatedAt) {

    public AnnotationDraftResponse {
        articleFields = List.copyOf(articleFields);
    }
}
