package com.law.annotation.dashboard;

import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.dashboard.dto.DashboardSummaryResponse;
import com.law.annotation.dashboard.dto.DashboardTodoItemResponse;
import com.law.annotation.dashboard.dto.DashboardTodoResponse;
import com.law.annotation.law.LawDisplayStatus;
import com.law.annotation.law.LawDisplayStatusResolver;
import com.law.annotation.law.LawDocument;
import com.law.annotation.law.LawErrorCodes;
import com.law.annotation.law.LawRepository;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.task.TaskRepository;
import com.law.annotation.task.TaskStateRules;
import com.law.annotation.task.TaskStatusProjection;
import com.law.annotation.version.ContentVersionDocument;
import com.law.annotation.version.ContentVersionRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final LawRepository lawRepository;
    private final ContentVersionRepository contentVersionRepository;
    private final TaskRepository taskRepository;
    private final LawDisplayStatusResolver displayStatusResolver;

    public DashboardService(
            LawRepository lawRepository,
            ContentVersionRepository contentVersionRepository,
            TaskRepository taskRepository,
            LawDisplayStatusResolver displayStatusResolver) {
        this.lawRepository = lawRepository;
        this.contentVersionRepository = contentVersionRepository;
        this.taskRepository = taskRepository;
        this.displayStatusResolver = displayStatusResolver;
    }

    public DashboardSummaryResponse getSummary() {
        List<LawDocument> laws = lawRepository.findAllByDeletedAtIsNull();
        if (laws.isEmpty()) {
            return new DashboardSummaryResponse(0, 0, 0, 0, 0, 0, 0, 0);
        }

        List<String> lawIds = laws.stream().map(LawDocument::getId).toList();
        Map<String, TaskStatusProjection> activeTasksByLawId = indexActiveTasks(
                taskRepository.findStatusesByLawIdInAndTaskStateIn(
                        lawIds,
                        TaskStateRules.unfinishedStates()));
        Map<String, ContentVersionDocument> currentVersionsById = contentVersionRepository
                .findByIdIn(laws.stream()
                        .map(LawDocument::getCurrentContentVersionId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(ContentVersionDocument::getId, Function.identity()));

        long totalArticles = 0;
        long unannotatedLaws = 0;
        long pendingRevisionLaws = 0;
        long completedLaws = 0;
        for (LawDocument law : laws) {
            ContentVersionDocument currentVersion = currentVersionsById.get(
                    law.getCurrentContentVersionId());
            if (currentVersion == null || !law.getId().equals(currentVersion.getLawId())) {
                throw versionInconsistent();
            }
            totalArticles += currentVersion.getSemanticArticlesSnapshot().size();

            LawDisplayStatus displayStatus = displayStatusResolver.resolve(
                    law,
                    activeTasksByLawId.get(law.getId()));
            switch (displayStatus) {
                case UNANNOTATED -> unannotatedLaws++;
                case PENDING_REVISION -> pendingRevisionLaws++;
                case COMPLETED -> completedLaws++;
                default -> {
                    // Other display states are represented by the task counters below.
                }
            }
        }

        long inProgressTasks = taskRepository.countByTaskStateInAndLawIdIn(
                TaskStateRules.unfinishedStates(), lawIds);
        long pendingReviewTasks = taskRepository.countByTaskStateAndLawIdIn(
                TaskState.PENDING_REVIEW, lawIds);
        long pendingRereviewTasks = taskRepository.countByTaskStateAndLawIdIn(
                TaskState.PENDING_REREVIEW, lawIds);
        return new DashboardSummaryResponse(
                laws.size(),
                totalArticles,
                unannotatedLaws,
                inProgressTasks,
                pendingReviewTasks,
                pendingRereviewTasks,
                pendingRevisionLaws,
                completedLaws);
    }

    public DashboardTodoResponse getTodos() {
        List<LawDocument> laws = lawRepository.findAllByDeletedAtIsNull();
        if (laws.isEmpty()) {
            return new DashboardTodoResponse(List.of(), List.of());
        }

        Map<String, LawDocument> lawsById = laws.stream()
                .collect(Collectors.toMap(LawDocument::getId, Function.identity()));
        List<String> lawIds = laws.stream().map(LawDocument::getId).toList();
        return new DashboardTodoResponse(
                toTodoItems(
                        taskRepository.findTop10ByTaskStateAndLawIdInOrderByCreatedAtDesc(
                                TaskState.PENDING_REVIEW,
                                lawIds),
                        lawsById),
                toTodoItems(
                        taskRepository.findTop10ByTaskStateAndLawIdInOrderByCreatedAtDesc(
                                TaskState.PENDING_REREVIEW,
                                lawIds),
                        lawsById));
    }

    private static Map<String, TaskStatusProjection> indexActiveTasks(
            List<TaskStatusProjection> activeTasks) {
        Map<String, TaskStatusProjection> tasksByLawId = new HashMap<>();
        activeTasks.forEach(task -> tasksByLawId.putIfAbsent(task.getLawId(), task));
        return Map.copyOf(tasksByLawId);
    }

    private static List<DashboardTodoItemResponse> toTodoItems(
            List<TaskDocument> tasks,
            Map<String, LawDocument> lawsById) {
        return tasks.stream()
                .filter(task -> lawsById.containsKey(task.getLawId()))
                .map(task -> new DashboardTodoItemResponse(
                        task.getTaskId(),
                        task.getTaskName(),
                        task.getTaskType(),
                        task.getLawId(),
                        lawsById.get(task.getLawId()).getName(),
                        task.getTaskState(),
                        task.getCreatedAt()))
                .toList();
    }

    private static ApiException versionInconsistent() {
        return new ApiException(
                HttpStatus.CONFLICT,
                LawErrorCodes.VERSION_INCONSISTENT,
                "法律当前内容版本数据不一致");
    }
}
