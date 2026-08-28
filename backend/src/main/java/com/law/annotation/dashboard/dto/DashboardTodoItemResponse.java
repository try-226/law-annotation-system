package com.law.annotation.dashboard.dto;

import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import java.time.Instant;

public record DashboardTodoItemResponse(
        String taskId,
        String taskName,
        TaskType taskType,
        String lawId,
        String lawName,
        TaskState taskState,
        Instant updatedAt) {
}
