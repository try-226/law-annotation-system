package com.law.annotation.task;

import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import java.time.Instant;
import org.springframework.data.annotation.Id;

/** Lightweight task view containing only fields required by dashboard todos. */
public record DashboardTodoTaskProjection(
        @Id String taskId,
        String taskName,
        TaskType taskType,
        TaskState taskState,
        String lawId,
        Instant updatedAt) {

    public String getTaskId() {
        return taskId;
    }

    public String getTaskName() {
        return taskName;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public TaskState getTaskState() {
        return taskState;
    }

    public String getLawId() {
        return lawId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
