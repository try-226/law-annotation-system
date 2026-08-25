package com.law.annotation.review;

import java.time.Instant;

public record ReviewIssue(
        String reviewRoundId,
        String taskId,
        ReviewScopeType scopeType,
        String articleId,
        String reason,
        Instant createdAt) {
}
