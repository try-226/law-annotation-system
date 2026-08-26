package com.law.annotation.review;

import com.law.annotation.common.enums.ReviewItemState;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "review_rounds")
public class ReviewRoundDocument {

    @Id
    private String reviewRoundId;
    private String taskId;
    private String lawId;
    private int roundNo;
    private ReviewRoundType roundType;
    private String sourceSubmissionId;
    private String previousSubmissionId;
    private String reviewerId;
    private List<ReviewItemLocator> requiredScope;
    private Map<String, ReviewItemState> itemStates;
    private Map<String, ReviewIssue> issues;
    private int totalCount;
    private int reviewedCount;
    private int unreviewedCount;
    private int needsChangeCount;
    private ReviewRoundOutcome completionOutcome;
    private Instant completionStartedAt;
    private String annotationVersionId;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;

    public ReviewRoundDocument() {
    }

    public ReviewRoundDocument(
            String reviewRoundId,
            String taskId,
            String lawId,
            int roundNo,
            ReviewRoundType roundType,
            String sourceSubmissionId,
            String previousSubmissionId,
            String reviewerId,
            List<ReviewItemLocator> requiredScope,
            Map<String, ReviewItemState> itemStates,
            Map<String, ReviewIssue> issues,
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
        this.reviewRoundId = reviewRoundId;
        this.taskId = taskId;
        this.lawId = lawId;
        this.roundNo = roundNo;
        this.roundType = roundType;
        this.sourceSubmissionId = sourceSubmissionId;
        this.previousSubmissionId = previousSubmissionId;
        this.reviewerId = reviewerId;
        this.requiredScope = requiredScope == null ? List.of() : List.copyOf(requiredScope);
        this.itemStates = itemStates == null ? Map.of() : new LinkedHashMap<>(itemStates);
        this.issues = issues == null ? Map.of() : new LinkedHashMap<>(issues);
        this.totalCount = totalCount;
        this.reviewedCount = reviewedCount;
        this.unreviewedCount = unreviewedCount;
        this.needsChangeCount = needsChangeCount;
        this.completionOutcome = completionOutcome;
        this.completionStartedAt = completionStartedAt;
        this.annotationVersionId = annotationVersionId;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    public String getReviewRoundId() { return reviewRoundId; }
    public String getTaskId() { return taskId; }
    public String getLawId() { return lawId; }
    public int getRoundNo() { return roundNo; }
    public ReviewRoundType getRoundType() { return roundType; }
    public String getSourceSubmissionId() { return sourceSubmissionId; }
    public String getPreviousSubmissionId() { return previousSubmissionId; }
    public String getReviewerId() { return reviewerId; }
    public List<ReviewItemLocator> getRequiredScope() {
        return requiredScope == null ? List.of() : List.copyOf(requiredScope);
    }
    public Map<String, ReviewItemState> getItemStates() {
        return itemStates == null ? Map.of() : Map.copyOf(itemStates);
    }
    public Map<String, ReviewIssue> getIssues() {
        return issues == null ? Map.of() : Map.copyOf(issues);
    }
    public int getTotalCount() { return totalCount; }
    public int getReviewedCount() { return reviewedCount; }
    public int getUnreviewedCount() { return unreviewedCount; }
    public int getNeedsChangeCount() { return needsChangeCount; }
    public ReviewRoundOutcome getCompletionOutcome() { return completionOutcome; }
    public Instant getCompletionStartedAt() { return completionStartedAt; }
    public String getAnnotationVersionId() { return annotationVersionId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
