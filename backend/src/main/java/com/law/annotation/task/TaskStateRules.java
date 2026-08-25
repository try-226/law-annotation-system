package com.law.annotation.task;

import com.law.annotation.common.enums.TaskState;
import java.util.List;

public final class TaskStateRules {

    public static final List<TaskState> UNFINISHED_STATES = List.of(
            TaskState.PENDING_ANNOTATION,
            TaskState.ANNOTATING,
            TaskState.PENDING_REVIEW,
            TaskState.PARTIALLY_REJECTED,
            TaskState.PENDING_REREVIEW);

    private TaskStateRules() {
    }
}
