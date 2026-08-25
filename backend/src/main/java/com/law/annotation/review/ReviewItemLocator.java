package com.law.annotation.review;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public record ReviewItemLocator(ReviewScopeType type, String articleId) {

    private static final String OVERALL_KEY = "OVERALL";
    private static final String ARTICLE_PREFIX = "ARTICLE_";

    public ReviewItemLocator {
        if (type == null) {
            throw new IllegalArgumentException("审核项类型不能为空");
        }
        if (type == ReviewScopeType.OVERALL) {
            articleId = null;
        } else if (articleId == null || articleId.isBlank()) {
            throw new IllegalArgumentException("法条审核项必须包含articleId");
        } else {
            articleId = articleId.trim();
        }
    }

    public static ReviewItemLocator overall() {
        return new ReviewItemLocator(ReviewScopeType.OVERALL, null);
    }

    public static ReviewItemLocator article(String articleId) {
        return new ReviewItemLocator(ReviewScopeType.ARTICLE, articleId);
    }

    public String storageKey() {
        if (type == ReviewScopeType.OVERALL) {
            return OVERALL_KEY;
        }
        return ARTICLE_PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(articleId.getBytes(StandardCharsets.UTF_8));
    }
}
