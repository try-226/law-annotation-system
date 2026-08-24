package com.law.annotation.annotation;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
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
        this.submissionId = submissionId;
        this.taskId = taskId;
        this.submissionNo = submissionNo;
        this.draftRevision = draftRevision;
        this.overallSnapshot = overallSnapshot;
        this.articleSnapshots = new LinkedHashMap<>(articleSnapshots);
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

    public String getSubmittedBy() {
        return submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
