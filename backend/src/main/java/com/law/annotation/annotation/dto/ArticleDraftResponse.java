package com.law.annotation.annotation.dto;

import com.law.annotation.annotation.ArticleAnnotationFields;

public record ArticleDraftResponse(
        String articleId,
        ArticleAnnotationFields fields,
        boolean filled) {
}
