package com.law.annotation.annotation.dto;

public record AnnotationProgressResponse(
        int totalArticles,
        int filledArticles,
        boolean overallCompleted) {
}
