package com.law.annotation.revision;

import com.law.annotation.review.ReviewItemLocator;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record RevisionScope(
        RevisionMode mode,
        boolean overall,
        List<String> articleIds,
        List<String> mandatoryArticleIds) {

    public RevisionScope {
        if (mode == null) {
            throw new IllegalArgumentException("revision mode不能为空");
        }
        articleIds = immutableIdentifiers(articleIds, "articleIds");
        mandatoryArticleIds = immutableIdentifiers(
                mandatoryArticleIds, "mandatoryArticleIds");
        if (!new LinkedHashSet<>(articleIds).containsAll(mandatoryArticleIds)) {
            throw new IllegalArgumentException("mandatoryArticleIds必须属于articleIds");
        }
        if (mode == RevisionMode.ANNOTATION_ONLY && !mandatoryArticleIds.isEmpty()) {
            throw new IllegalArgumentException("annotation-only revision不能包含mandatory scope");
        }
    }

    public boolean includesArticle(String articleId) {
        return articleIds.contains(articleId);
    }

    public List<ReviewItemLocator> toReviewScope() {
        List<ReviewItemLocator> scope = new ArrayList<>();
        if (overall) {
            scope.add(ReviewItemLocator.overall());
        }
        articleIds.forEach(articleId -> scope.add(ReviewItemLocator.article(articleId)));
        return List.copyOf(scope);
    }

    private static List<String> immutableIdentifiers(List<String> values, String field) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + "不能包含空标识");
            }
            normalized.add(value.trim());
        }
        return List.copyOf(normalized);
    }
}
