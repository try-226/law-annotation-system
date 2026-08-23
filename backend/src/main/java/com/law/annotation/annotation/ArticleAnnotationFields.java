package com.law.annotation.annotation;

import com.law.annotation.common.enums.ItemType;

public record ArticleAnnotationFields(
        ItemType itemType,
        String keywords,
        String subjects,
        String legalLiability,
        String annotationNote) {
}
