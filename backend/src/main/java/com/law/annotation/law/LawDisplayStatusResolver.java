package com.law.annotation.law;

import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.task.TaskRepository;
import com.law.annotation.task.TaskStateRules;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LawDisplayStatusResolver {

    private final TaskRepository taskRepository;

    public LawDisplayStatusResolver(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public Map<String, LawDisplayStatus> resolve(List<LawDocument> laws) {
        if (laws.isEmpty()) {
            return Map.of();
        }
        List<String> lawIds = laws.stream().map(LawDocument::getId).toList();
        List<TaskDocument> tasks = taskRepository.findByLawIdInAndTaskStateIn(
                lawIds,
                TaskStateRules.unfinishedStates());
        Map<String, TaskDocument> revisionTasks = tasksByType(tasks, TaskType.REVISION);
        Map<String, TaskDocument> ordinaryTasks = tasksByType(tasks, TaskType.ORDINARY);
        Map<String, LawDisplayStatus> statuses = new LinkedHashMap<>();
        for (LawDocument law : laws) {
            statuses.put(law.getId(), derive(
                    law,
                    revisionTasks.get(law.getId()),
                    ordinaryTasks.get(law.getId())));
        }
        return Map.copyOf(statuses);
    }

    private static Map<String, TaskDocument> tasksByType(
            Collection<TaskDocument> tasks,
            TaskType taskType) {
        Map<String, TaskDocument> byLawId = new HashMap<>();
        tasks.stream()
                .filter(task -> task.getTaskType() == taskType)
                .forEach(task -> byLawId.putIfAbsent(task.getLawId(), task));
        return byLawId;
    }

    private static LawDisplayStatus derive(
            LawDocument law,
            TaskDocument revisionTask,
            TaskDocument ordinaryTask) {
        if (revisionTask != null) {
            return LawDisplayStatus.REVISING;
        }
        if (law.isPendingRevision()) {
            return LawDisplayStatus.PENDING_REVISION;
        }
        if (ordinaryTask != null) {
            return fromOrdinaryTaskState(ordinaryTask.getTaskState());
        }
        if (law.getCurrentAnnotationVersionId() != null) {
            return LawDisplayStatus.COMPLETED;
        }
        return LawDisplayStatus.UNANNOTATED;
    }

    private static LawDisplayStatus fromOrdinaryTaskState(TaskState state) {
        return switch (state) {
            case PENDING_ANNOTATION -> LawDisplayStatus.PENDING_ANNOTATION;
            case ANNOTATING -> LawDisplayStatus.ANNOTATING;
            case PENDING_REVIEW -> LawDisplayStatus.PENDING_REVIEW;
            case PARTIALLY_REJECTED -> LawDisplayStatus.PARTIALLY_REJECTED;
            case PENDING_REREVIEW -> LawDisplayStatus.PENDING_REREVIEW;
            case APPROVED, CANCELED -> throw new IllegalArgumentException(
                    "已结束任务不能用于派生法律展示状态");
        };
    }
}
