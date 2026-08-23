package com.law.annotation.annotation.dto;

import com.law.annotation.task.dto.TaskDetailResponse;

public record AnnotationWorkbenchResponse(
        TaskDetailResponse task,
        AnnotationDraftResponse draft) {
}
