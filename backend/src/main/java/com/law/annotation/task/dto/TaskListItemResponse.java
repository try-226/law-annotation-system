package com.law.annotation.task.dto;

import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import java.time.Instant;

public record TaskListItemResponse(
        String taskId,
        String taskName,
        TaskType taskType,
        String lawId,
        String lawName,
        String annotatorId,
        String annotatorName,
        TaskState taskState,
        String remark,
        Instant createdAt) {
}
