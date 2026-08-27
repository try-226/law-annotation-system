package com.law.annotation.history.dto;

import com.law.annotation.law.ArticleSnapshot;
import java.time.Instant;
import java.util.List;

public record ContentVersionHistoryResponse(
        String contentVersionId,
        String lawId,
        int seq,
        List<ArticleSnapshot> semanticArticlesSnapshot,
        String createdBy,
        Instant createdAt) {

    public ContentVersionHistoryResponse {
        semanticArticlesSnapshot = List.copyOf(semanticArticlesSnapshot);
    }
}
