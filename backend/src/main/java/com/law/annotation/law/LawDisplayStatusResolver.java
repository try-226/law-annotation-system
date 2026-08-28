package com.law.annotation.law;

import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.task.TaskStateRules;
import com.law.annotation.task.TaskStatusProjection;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class LawDisplayStatusResolver {

    public LawDisplayStatus resolve(LawDocument law, TaskStatusProjection activeTask) {
        Objects.requireNonNull(law, "law must not be null");
        return resolve(law.isPendingRevision(), law.getCurrentAnnotationVersionId(), activeTask);
    }

    public LawDisplayStatus resolve(LawDashboardProjection law, TaskStatusProjection activeTask) {
        Objects.requireNonNull(law, "law must not be null");
        return resolve(law.isPendingRevision(), law.getCurrentAnnotationVersionId(), activeTask);
    }

    private LawDisplayStatus resolve(
            boolean pendingRevision,
            String currentAnnotationVersionId,
            TaskStatusProjection activeTask) {
        if (activeTask != null) {
            return resolveActiveTask(activeTask);
        }
        if (pendingRevision) {
            return LawDisplayStatus.PENDING_REVISION;
        }
        if (currentAnnotationVersionId != null) {
            return LawDisplayStatus.COMPLETED;
        }
        return LawDisplayStatus.UNANNOTATED;
    }

    public LawDisplayStatus resolveActiveTask(TaskStatusProjection task) {
        Objects.requireNonNull(task, "task must not be null");
        TaskType type = Objects.requireNonNull(task.getTaskType(), "taskType must not be null");
        TaskState state = Objects.requireNonNull(task.getTaskState(), "taskState must not be null");
        if (!TaskStateRules.unfinishedStates().contains(state)) {
            throw new IllegalArgumentException("仅未结束任务可用于计算法律展示状态");
        }
        if (type == TaskType.REVISION) {
            return LawDisplayStatus.REVISING;
        }
        return switch (state) {
            case PENDING_ANNOTATION, ANNOTATING -> LawDisplayStatus.ANNOTATING;
            case PENDING_REVIEW -> LawDisplayStatus.PENDING_REVIEW;
            case PARTIALLY_REJECTED -> LawDisplayStatus.PARTIALLY_REJECTED;
            case PENDING_REREVIEW -> LawDisplayStatus.PENDING_REREVIEW;
            case APPROVED, CANCELED -> throw new IllegalArgumentException(
                    "仅未结束任务可用于计算法律展示状态");
        };
    }
}
