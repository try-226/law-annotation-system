package com.law.annotation.annotation.dto;

import com.law.annotation.annotation.ArticleDraftValues;
import com.law.annotation.annotation.OverallDraftValues;
import com.law.annotation.common.enums.TaskState;
import java.time.Instant;
import java.util.Map;
import java.util.List;

public record TaskDraftResponse(
        String taskId,
        TaskState taskState,
        OverallDraftValues overallDraft,
        Map<String, ArticleDraftValues> articleDrafts,
        EditableScopeResponse editableScope,
        AnnotationProgressResponse progress,
        List<ReviewIssueFeedbackResponse> reviewIssues,
        long revision,
        Instant updatedAt) {

    public TaskDraftResponse {
        articleDrafts = Map.copyOf(articleDrafts);
        reviewIssues = List.copyOf(reviewIssues);
    }

    public TaskDraftResponse(
            String taskId,
            TaskState taskState,
            OverallDraftValues overallDraft,
            Map<String, ArticleDraftValues> articleDrafts,
            EditableScopeResponse editableScope,
            AnnotationProgressResponse progress,
            long revision,
            Instant updatedAt) {
        this(taskId, taskState, overallDraft, articleDrafts, editableScope,
                progress, List.of(), revision, updatedAt);
    }
}
