package com.law.annotation.annotation;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.law.annotation.review.ReviewItemLocator;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "task_submissions")
public class TaskSubmissionDocument {

    @Id
    private String submissionId;
    private String taskId;
    private int submissionNo;
    private long draftRevision;
    private OverallDraftValues overallSnapshot;
    private Map<String, ArticleDraftValues> articleSnapshots;
    private String sourceReviewRoundId;
    private List<ReviewItemLocator> modifiedScope;
    private String submittedBy;
    private Instant submittedAt;

    public TaskSubmissionDocument() {
    }

    public TaskSubmissionDocument(
            String submissionId,
            String taskId,
            int submissionNo,
            long draftRevision,
            OverallDraftValues overallSnapshot,
            Map<String, ArticleDraftValues> articleSnapshots,
            String submittedBy,
            Instant submittedAt) {
        this(submissionId, taskId, submissionNo, draftRevision, overallSnapshot,
                articleSnapshots, null, List.of(), submittedBy, submittedAt);
    }

    public TaskSubmissionDocument(
            String submissionId,
            String taskId,
            int submissionNo,
            long draftRevision,
            OverallDraftValues overallSnapshot,
            Map<String, ArticleDraftValues> articleSnapshots,
            String sourceReviewRoundId,
            List<ReviewItemLocator> modifiedScope,
            String submittedBy,
            Instant submittedAt) {
        this.submissionId = submissionId;
        this.taskId = taskId;
        this.submissionNo = submissionNo;
        this.draftRevision = draftRevision;
        this.overallSnapshot = overallSnapshot;
        this.articleSnapshots = new LinkedHashMap<>(articleSnapshots);
        this.sourceReviewRoundId = sourceReviewRoundId;
        this.modifiedScope = modifiedScope == null ? List.of() : List.copyOf(modifiedScope);
        this.submittedBy = submittedBy;
        this.submittedAt = submittedAt;
    }

    public String getSubmissionId() {
        return submissionId;
    }

    public String getTaskId() {
        return taskId;
    }

    public int getSubmissionNo() {
        return submissionNo;
    }

    public long getDraftRevision() {
        return draftRevision;
    }

    public OverallDraftValues getOverallSnapshot() {
        return overallSnapshot;
    }

    public Map<String, ArticleDraftValues> getArticleSnapshots() {
        return articleSnapshots == null ? Map.of() : Map.copyOf(articleSnapshots);
    }

    public String getSourceReviewRoundId() {
        return sourceReviewRoundId;
    }

    public List<ReviewItemLocator> getModifiedScope() {
        return modifiedScope == null ? List.of() : List.copyOf(modifiedScope);
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
