package com.law.annotation.annotation.dto;

public record SaveOverallDraftRequest(
        String lawCategory,
        String overallKeywords,
        String summary,
        String overallNote) {
}
