package com.law.annotation.law;

import com.law.annotation.common.enums.TaskType;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.task.TaskRepository;
import com.law.annotation.task.TaskStateRules;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class LawDisplayStatusResolver {

    private final TaskRepository taskRepository;

    public LawDisplayStatusResolver(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    Map<String, Resolution> resolveAll(List<LawDocument> laws) {
        if (laws.isEmpty()) {
            return Map.of();
        }
        List<String> lawIds = laws.stream()
                .map(LawDocument::getId)
                .distinct()
                .toList();
        Set<String> requestedLawIds = Set.copyOf(lawIds);
        Map<String, TaskDocument> activeTasksByLawId = new HashMap<>();
        for (TaskDocument task : taskRepository.findUnfinishedByLawIds(lawIds)) {
            if (!requestedLawIds.contains(task.getLawId())) {
                throw new IllegalStateException("未结束任务查询返回了范围外法律");
            }
            TaskDocument duplicate = activeTasksByLawId.putIfAbsent(task.getLawId(), task);
            if (duplicate != null) {
                throw new IllegalStateException("同一法律存在多个未结束任务");
            }
        }

        Map<String, Resolution> resolutions = new LinkedHashMap<>();
        for (LawDocument law : laws) {
            resolutions.put(law.getId(), resolve(law, activeTasksByLawId.get(law.getId())));
        }
        return Map.copyOf(resolutions);
    }

    Resolution resolve(LawDocument law) {
        return resolveAll(List.of(law)).get(law.getId());
    }

    static Resolution resolve(LawDocument law, TaskDocument task) {
        Objects.requireNonNull(law, "law");
        boolean maintenanceLocked = task != null
                && TaskStateRules.UNFINISHED_STATES.contains(task.getTaskState());
        if (!maintenanceLocked) {
            if (law.isPendingRevision()) {
                return new Resolution(LawDisplayStatus.PENDING_REVISION, false);
            }
            if (law.getCurrentAnnotationVersionId() != null) {
                return new Resolution(LawDisplayStatus.COMPLETED, false);
            }
            return new Resolution(LawDisplayStatus.UNANNOTATED, false);
        }

        if (task.getTaskType() == TaskType.REVISION) {
            return new Resolution(LawDisplayStatus.REVISING, true);
        }
        if (task.getTaskType() != TaskType.ORDINARY) {
            throw new IllegalStateException("未知任务类型无法派生法律展示状态");
        }
        LawDisplayStatus displayStatus = switch (task.getTaskState()) {
            case PENDING_ANNOTATION, ANNOTATING -> LawDisplayStatus.ANNOTATING;
            case PENDING_REVIEW -> LawDisplayStatus.PENDING_REVIEW;
            case PARTIALLY_REJECTED -> LawDisplayStatus.PARTIALLY_REJECTED;
            case PENDING_REREVIEW -> LawDisplayStatus.PENDING_REREVIEW;
            case APPROVED, CANCELED -> throw new IllegalStateException(
                    "终态任务不能作为未结束任务派生法律展示状态");
        };
        return new Resolution(displayStatus, true);
    }

    record Resolution(LawDisplayStatus displayStatus, boolean maintenanceLocked) {
    }
}
