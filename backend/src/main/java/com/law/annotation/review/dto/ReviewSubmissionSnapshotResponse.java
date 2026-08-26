package com.law.annotation.review.dto;

import com.law.annotation.annotation.ArticleDraftValues;
import com.law.annotation.annotation.OverallDraftValues;
import com.law.annotation.annotation.TaskSubmissionDocument;
import java.time.Instant;
import java.util.Map;

public record ReviewSubmissionSnapshotResponse(
        String submissionId,
        int submissionNo,
        OverallDraftValues overall,
        Map<String, ArticleDraftValues> articles,
        Instant submittedAt) {

    public ReviewSubmissionSnapshotResponse {
        articles = Map.copyOf(articles);
    }

    public static ReviewSubmissionSnapshotResponse from(TaskSubmissionDocument submission) {
        if (submission == null) {
            return null;
        }
        return new ReviewSubmissionSnapshotResponse(
                submission.getSubmissionId(),
                submission.getSubmissionNo(),
                submission.getOverallSnapshot(),
                submission.getArticleSnapshots(),
                submission.getSubmittedAt());
    }
}
