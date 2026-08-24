package com.law.annotation.annotation.dto;

import com.law.annotation.annotation.ArticleDraftValues;
import com.law.annotation.annotation.OverallDraftValues;
import com.law.annotation.common.enums.TaskState;
import java.time.Instant;
import java.util.Map;

public record TaskDraftResponse(
        String taskId,
        TaskState taskState,
        OverallDraftValues overallDraft,
        Map<String, ArticleDraftValues> articleDrafts,
        EditableScopeResponse editableScope,
        AnnotationProgressResponse progress,
        long revision,
        Instant updatedAt) {

    public TaskDraftResponse {
        articleDrafts = Map.copyOf(articleDrafts);
    }
}
