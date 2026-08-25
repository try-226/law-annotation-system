package com.law.annotation.annotation;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "task_drafts")
public class TaskDraftDocument {

    @Id
    private String taskId;
    private OverallDraftValues overallDraft;
    private Map<String, ArticleDraftValues> perArticleDrafts = new LinkedHashMap<>();
    private Map<String, String> reviewSaveMarkers = new LinkedHashMap<>();
    private long revision;
    private String updatedBy;
    private Instant createdAt;
    private Instant updatedAt;

    public TaskDraftDocument() {
    }

    public TaskDraftDocument(
            String taskId,
            OverallDraftValues overallDraft,
            Map<String, ArticleDraftValues> perArticleDrafts,
            long revision,
            String updatedBy,
            Instant createdAt,
            Instant updatedAt) {
        this.taskId = taskId;
        this.overallDraft = overallDraft;
        this.perArticleDrafts = new LinkedHashMap<>(perArticleDrafts);
        this.reviewSaveMarkers = new LinkedHashMap<>();
        this.revision = revision;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getTaskId() {
        return taskId;
    }

    public OverallDraftValues getOverallDraft() {
        return overallDraft;
    }

    public Map<String, ArticleDraftValues> getPerArticleDrafts() {
        return perArticleDrafts == null ? Map.of() : Map.copyOf(perArticleDrafts);
    }

    public Map<String, String> getReviewSaveMarkers() {
        return reviewSaveMarkers == null ? Map.of() : Map.copyOf(reviewSaveMarkers);
    }

    public long getRevision() {
        return revision;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
