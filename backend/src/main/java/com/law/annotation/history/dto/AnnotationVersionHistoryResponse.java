package com.law.annotation.history.dto;

import com.law.annotation.annotation.ArticleDraftValues;
import com.law.annotation.annotation.OverallDraftValues;
import java.time.Instant;
import java.util.List;

public record AnnotationVersionHistoryResponse(
        String annotationVersionId,
        String lawId,
        int seq,
        String contentVersionId,
        OverallDraftValues overallResult,
        List<ArticleResult> articleResults,
        String sourceTaskId,
        String sourceSubmissionId,
        String approvedBy,
        Instant approvedAt) {

    public AnnotationVersionHistoryResponse {
        articleResults = List.copyOf(articleResults);
    }

    public record ArticleResult(String articleId, ArticleDraftValues values) {
    }
}
