package com.law.annotation.annotation.dto;

public record SaveArticleDraftRequest(
        String itemType,
        String keywords,
        String subjects,
        String legalLiability,
        String annotationNote) {
}
