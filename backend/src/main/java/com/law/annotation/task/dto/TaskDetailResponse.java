package com.law.annotation.task.dto;

import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.field.FieldConfigSnapshot;
import com.law.annotation.task.TaskContentVersionSnapshot;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.task.TaskLawBaseInfoSnapshot;
import com.law.annotation.task.TaskStructureNodeSnapshot;
import com.law.annotation.revision.RevisionScope;
import java.time.Instant;
import java.util.List;

public record TaskDetailResponse(
        String taskId,
        TaskType taskType,
        TaskState taskState,
        String lawId,
        String annotatorId,
        String annotatorName,
        String taskName,
        String remark,
        String contentVersionId,
        TaskContentVersionSnapshot contentVersionSnapshot,
        TaskLawBaseInfoSnapshot lawBaseInfoSnapshot,
        List<TaskStructureNodeSnapshot> structureSnapshot,
        FieldConfigSnapshot fieldConfigSnapshot,
        String baseAnnotationVersionId,
        RevisionScope revisionScope,
        String createdBy,
        String cancelReason,
        String canceledBy,
        Instant canceledAt,
        Instant createdAt,
        Instant updatedAt) {

    public TaskDetailResponse {
        structureSnapshot = List.copyOf(structureSnapshot);
    }

    public static TaskDetailResponse from(TaskDocument task) {
        return new TaskDetailResponse(
                task.getTaskId(),
                task.getTaskType(),
                task.getTaskState(),
                task.getLawId(),
                task.getAnnotatorId(),
                task.getAnnotatorNameSnapshot(),
                task.getTaskName(),
                task.getRemark(),
                task.getContentVersionId(),
                task.getContentVersionSnapshot(),
                task.getLawBaseInfoSnapshot(),
                task.getStructureSnapshot(),
                task.getFieldConfigSnapshot(),
                task.getBaseAnnotationVersionId(),
                task.getRevisionScope(),
                task.getCreatedBy(),
                task.getCancelReason(),
                task.getCanceledBy(),
                task.getCanceledAt(),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }
}
