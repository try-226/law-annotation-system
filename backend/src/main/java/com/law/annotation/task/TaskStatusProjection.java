package com.law.annotation.task;

import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;

/** Lightweight task status view for read-only law display-status queries. */
public interface TaskStatusProjection {

    String getLawId();

    TaskType getTaskType();

    TaskState getTaskState();
}
