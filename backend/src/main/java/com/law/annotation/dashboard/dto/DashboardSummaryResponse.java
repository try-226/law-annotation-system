package com.law.annotation.dashboard.dto;

public record DashboardSummaryResponse(
        long totalLaws,
        long totalArticles,
        long unannotatedLaws,
        long inProgressTasks,
        long pendingReviewTasks,
        long pendingRereviewTasks,
        long pendingRevisionLaws,
        long completedLaws) {
}
