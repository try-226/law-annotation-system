package com.law.annotation.history.dto;

import com.law.annotation.annotation.ArticleDraftValues;
import com.law.annotation.annotation.OverallDraftValues;
import com.law.annotation.common.enums.ReviewItemState;
import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.field.FieldConfigSnapshot;
import com.law.annotation.revision.RevisionScope;
import com.law.annotation.review.ReviewItemLocator;
import com.law.annotation.review.ReviewRoundOutcome;
import com.law.annotation.review.ReviewRoundType;
import com.law.annotation.task.TaskContentVersionSnapshot;
import com.law.annotation.task.TaskLawBaseInfoSnapshot;
import com.law.annotation.task.TaskStructureNodeSnapshot;
import java.time.Instant;
import java.util.List;

public record TaskHistoryResponse(
        String taskId,
        TaskType taskType,
        TaskState taskState,
        String taskName,
        String remark,
        String lawId,
        boolean lawDeleted,
        Instant lawDeletedAt,
        String annotatorId,
        String annotatorNameSnapshot,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        String contentVersionId,
        TaskContentVersionSnapshot contentVersionSnapshot,
        TaskLawBaseInfoSnapshot lawBaseInfoSnapshot,
        List<TaskStructureNodeSnapshot> structureSnapshot,
        FieldConfigSnapshot fieldConfigSnapshot,
        String baseAnnotationVersionId,
        RevisionScope revisionScope,
        String initialSubmissionId,
        String currentSubmissionId,
        String currentReviewRoundId,
        String approvedAnnotationVersionId,
        String cancelReason,
        String canceledBy,
        Instant canceledAt,
        List<Submission> submissions,
        List<ReviewRound> reviewRounds) {

    public TaskHistoryResponse {
        structureSnapshot = List.copyOf(structureSnapshot);
        submissions = List.copyOf(submissions);
        reviewRounds = List.copyOf(reviewRounds);
    }

    public record Submission(
            String submissionId,
            int submissionNo,
            long draftRevision,
            OverallDraftValues overallSnapshot,
            List<ArticleResult> articleSnapshots,
            String sourceReviewRoundId,
            List<ReviewItemLocator> modifiedScope,
            String submittedBy,
            Instant submittedAt) {

        public Submission {
            articleSnapshots = List.copyOf(articleSnapshots);
            modifiedScope = List.copyOf(modifiedScope);
        }
    }

    public record ArticleResult(String articleId, ArticleDraftValues values) {
    }

    public record ReviewRound(
            String reviewRoundId,
            int roundNo,
            ReviewRoundType roundType,
            String sourceSubmissionId,
            String previousSubmissionId,
            String reviewerId,
            List<ReviewItemLocator> requiredScope,
            List<ItemState> itemStates,
            List<ReviewIssueHistory> issues,
            int totalCount,
            int reviewedCount,
            int unreviewedCount,
            int needsChangeCount,
            ReviewRoundOutcome completionOutcome,
            Instant completionStartedAt,
            String annotationVersionId,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt) {

        public ReviewRound {
            requiredScope = List.copyOf(requiredScope);
            itemStates = List.copyOf(itemStates);
            issues = List.copyOf(issues);
        }
    }

    public record ItemState(ReviewItemLocator locator, ReviewItemState state) {
    }

    public record ReviewIssueHistory(
            ReviewItemLocator locator,
            String reason,
            String actorId,
            Instant createdAt) {
    }
}
