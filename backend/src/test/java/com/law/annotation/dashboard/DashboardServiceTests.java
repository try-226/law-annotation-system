package com.law.annotation.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.dashboard.dto.DashboardSummaryResponse;
import com.law.annotation.dashboard.dto.DashboardTodoResponse;
import com.law.annotation.law.LawDisplayStatusResolver;
import com.law.annotation.law.LawDashboardProjection;
import com.law.annotation.law.LawRepository;
import com.law.annotation.task.DashboardTodoTaskProjection;
import com.law.annotation.task.TaskRepository;
import com.law.annotation.task.TaskStateRules;
import com.law.annotation.task.TaskStatusProjection;
import com.law.annotation.version.ContentVersionArticleCountProjection;
import com.law.annotation.version.ContentVersionRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class DashboardServiceTests {

    private static final Instant T0 = Instant.parse("2026-08-27T00:00:00Z");

    private LawRepository lawRepository;
    private ContentVersionRepository contentVersionRepository;
    private TaskRepository taskRepository;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        lawRepository = mock(LawRepository.class);
        contentVersionRepository = mock(ContentVersionRepository.class);
        taskRepository = mock(TaskRepository.class);
        service = new DashboardService(
                lawRepository,
                contentVersionRepository,
                taskRepository,
                new LawDisplayStatusResolver());
    }

    @Test
    void summaryUsesOnlyCurrentContentAndUnifiedLawDisplayStatusPriority() {
        LawDashboardProjection unannotated = law("law-unannotated", "c-unannotated", null, false);
        LawDashboardProjection annotating = law("law-annotating", "c-annotating", null, false);
        LawDashboardProjection completed = law("law-completed", "c-completed", "a-completed", false);
        LawDashboardProjection pending = law("law-pending", "c-pending", "a-pending", true);
        LawDashboardProjection revising = law("law-revising", "c-revising", "a-revising", true);
        List<LawDashboardProjection> laws = List.of(
                unannotated, annotating, completed, pending, revising);
        List<ContentVersionArticleCountProjection> articleCounts = List.of(
                articleCount("c-unannotated", "law-unannotated", 2),
                articleCount("c-annotating", "law-annotating", 1),
                articleCount("c-completed", "law-completed", 3),
                articleCount("c-pending", "law-pending", 4),
                articleCount("c-revising", "law-revising", 5));
        when(lawRepository.findDashboardLaws()).thenReturn(laws);
        when(contentVersionRepository.findArticleCountsByIdIn(List.of(
                        "c-unannotated",
                        "c-annotating",
                        "c-completed",
                        "c-pending",
                        "c-revising")))
                .thenReturn(articleCounts);
        TaskStatusProjection ordinaryTask = taskStatus(
                "law-annotating",
                TaskType.ORDINARY,
                TaskState.PENDING_ANNOTATION);
        TaskStatusProjection revisionTask = taskStatus(
                "law-revising",
                TaskType.REVISION,
                TaskState.PENDING_REVIEW);
        when(taskRepository.findStatusesByLawIdInAndTaskStateIn(
                        List.of(
                                "law-unannotated",
                                "law-annotating",
                                "law-completed",
                                "law-pending",
                                "law-revising"),
                        TaskStateRules.unfinishedStates()))
                .thenReturn(List.of(ordinaryTask, revisionTask));
        when(taskRepository.countByTaskStateInAndLawIdIn(
                        TaskStateRules.unfinishedStates(),
                        laws.stream().map(LawDashboardProjection::getId).toList()))
                .thenReturn(2L);
        when(taskRepository.countByTaskStateAndLawIdIn(
                        TaskState.PENDING_REVIEW,
                        laws.stream().map(LawDashboardProjection::getId).toList()))
                .thenReturn(1L);
        when(taskRepository.countByTaskStateAndLawIdIn(
                        TaskState.PENDING_REREVIEW,
                        laws.stream().map(LawDashboardProjection::getId).toList()))
                .thenReturn(0L);

        DashboardSummaryResponse result = service.getSummary();

        assertThat(result.totalLaws()).isEqualTo(5);
        assertThat(result.totalArticles()).isEqualTo(15);
        assertThat(result.unannotatedLaws()).isEqualTo(1);
        assertThat(result.inProgressTasks()).isEqualTo(2);
        assertThat(result.pendingReviewTasks()).isEqualTo(1);
        assertThat(result.pendingRereviewTasks()).isZero();
        assertThat(result.pendingRevisionLaws()).isEqualTo(1);
        assertThat(result.completedLaws()).isEqualTo(1);
        verify(lawRepository, never()).findAllByDeletedAtIsNull();
        verify(contentVersionRepository, never()).findByIdIn(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void summaryDelegatesActiveAndReviewCountsToPreciseDatabaseQueries() {
        LawDashboardProjection law = law("law-1", "c-1", null, false);
        ContentVersionArticleCountProjection count = articleCount("c-1", "law-1", 1);
        when(lawRepository.findDashboardLaws()).thenReturn(List.of(law));
        when(contentVersionRepository.findArticleCountsByIdIn(List.of("c-1")))
                .thenReturn(List.of(count));
        when(taskRepository.findStatusesByLawIdInAndTaskStateIn(
                        List.of("law-1"), TaskStateRules.unfinishedStates()))
                .thenReturn(List.of());
        when(taskRepository.countByTaskStateInAndLawIdIn(
                        TaskStateRules.unfinishedStates(), List.of("law-1")))
                .thenReturn(5L);
        when(taskRepository.countByTaskStateAndLawIdIn(
                        TaskState.PENDING_REVIEW, List.of("law-1")))
                .thenReturn(2L);
        when(taskRepository.countByTaskStateAndLawIdIn(
                        TaskState.PENDING_REREVIEW, List.of("law-1")))
                .thenReturn(1L);

        DashboardSummaryResponse result = service.getSummary();

        assertThat(TaskStateRules.unfinishedStates()).containsExactly(
                TaskState.PENDING_ANNOTATION,
                TaskState.ANNOTATING,
                TaskState.PENDING_REVIEW,
                TaskState.PARTIALLY_REJECTED,
                TaskState.PENDING_REREVIEW);
        assertThat(result.inProgressTasks()).isEqualTo(5);
        assertThat(result.pendingReviewTasks()).isEqualTo(2);
        assertThat(result.pendingRereviewTasks()).isEqualTo(1);
        verify(taskRepository).countByTaskStateInAndLawIdIn(
                TaskStateRules.unfinishedStates(), List.of("law-1"));
        verify(taskRepository).countByTaskStateAndLawIdIn(
                TaskState.PENDING_REVIEW, List.of("law-1"));
        verify(taskRepository).countByTaskStateAndLawIdIn(
                TaskState.PENDING_REREVIEW, List.of("law-1"));
    }

    @Test
    void todosAreSeparatedByExactStateAndDefensivelyExcludeDeletedLawTasks() {
        LawDashboardProjection firstLaw = law("law-1", "c-1", null, false);
        LawDashboardProjection secondLaw = law("law-2", "c-2", null, false);
        when(lawRepository.findDashboardLaws())
                .thenReturn(List.of(firstLaw, secondLaw));
        DashboardTodoTaskProjection review = task(
                "task-review", "law-1", TaskType.ORDINARY, TaskState.PENDING_REVIEW, T0);
        DashboardTodoTaskProjection rereview = task(
                "task-rereview",
                "law-2",
                TaskType.REVISION,
                TaskState.PENDING_REREVIEW,
                T0.plusSeconds(1));
        DashboardTodoTaskProjection deletedLawTask = task(
                "task-deleted",
                "deleted-law",
                TaskType.ORDINARY,
                TaskState.PENDING_REVIEW,
                T0.plusSeconds(2));
        when(taskRepository.findDashboardTodos(
                        TaskState.PENDING_REVIEW,
                        List.of("law-1", "law-2"),
                        PageRequest.of(0, 10)))
                .thenReturn(List.of(deletedLawTask, review));
        when(taskRepository.findDashboardTodos(
                        TaskState.PENDING_REREVIEW,
                        List.of("law-1", "law-2"),
                        PageRequest.of(0, 10)))
                .thenReturn(List.of(rereview));

        DashboardTodoResponse result = service.getTodos();

        assertThat(result.pendingReview()).singleElement().satisfies(item -> {
            assertThat(item.taskId()).isEqualTo("task-review");
            assertThat(item.lawId()).isEqualTo("law-1");
            assertThat(item.lawName()).isEqualTo("law-1名称");
            assertThat(item.taskState()).isEqualTo(TaskState.PENDING_REVIEW);
            assertThat(item.updatedAt()).isEqualTo(T0);
        });
        assertThat(result.pendingRereview()).singleElement().satisfies(item -> {
            assertThat(item.taskId()).isEqualTo("task-rereview");
            assertThat(item.taskType()).isEqualTo(TaskType.REVISION);
            assertThat(item.taskState()).isEqualTo(TaskState.PENDING_REREVIEW);
        });
    }

    @Test
    void emptyVisibleLawSetReturnsZerosWithoutTaskOrVersionQueries() {
        when(lawRepository.findDashboardLaws()).thenReturn(List.of());

        DashboardSummaryResponse summary = service.getSummary();
        DashboardTodoResponse todos = service.getTodos();

        assertThat(summary).isEqualTo(new DashboardSummaryResponse(0, 0, 0, 0, 0, 0, 0, 0));
        assertThat(todos.pendingReview()).isEmpty();
        assertThat(todos.pendingRereview()).isEmpty();
        verifyNoInteractions(contentVersionRepository);
        verifyNoInteractions(taskRepository);
    }

    @Test
    void inconsistentCurrentContentVersionUsesExistingLawErrorContract() {
        LawDashboardProjection law = law("law-1", "c-current", null, false);
        ContentVersionArticleCountProjection count =
                articleCount("c-current", "other-law", 1);
        when(lawRepository.findDashboardLaws()).thenReturn(List.of(law));
        when(contentVersionRepository.findArticleCountsByIdIn(List.of("c-current")))
                .thenReturn(List.of(count));
        when(taskRepository.findStatusesByLawIdInAndTaskStateIn(
                        List.of("law-1"), TaskStateRules.unfinishedStates()))
                .thenReturn(List.of());

        assertThatThrownBy(service::getSummary)
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus().value()).isEqualTo(409);
                    assertThat(error.getCode()).isEqualTo("LAW.VERSION_INCONSISTENT");
                });

        verify(taskRepository, never()).countByTaskStateInAndLawIdIn(
                TaskStateRules.unfinishedStates(), List.of("law-1"));
    }

    @Test
    void missingCurrentContentVersionUsesExistingLawErrorContract() {
        LawDashboardProjection law = law("law-1", "c-missing", null, false);
        when(lawRepository.findDashboardLaws()).thenReturn(List.of(law));
        when(contentVersionRepository.findArticleCountsByIdIn(List.of("c-missing")))
                .thenReturn(List.of());
        when(taskRepository.findStatusesByLawIdInAndTaskStateIn(
                        List.of("law-1"), TaskStateRules.unfinishedStates()))
                .thenReturn(List.of());

        assertThatThrownBy(service::getSummary)
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus().value()).isEqualTo(409);
                    assertThat(error.getCode()).isEqualTo("LAW.VERSION_INCONSISTENT");
                });
    }

    private static LawDashboardProjection law(
            String id,
            String contentVersionId,
            String annotationVersionId,
            boolean pendingRevision) {
        return new LawDashboardProjection(
                id,
                id + "名称",
                contentVersionId,
                annotationVersionId,
                pendingRevision);
    }

    private static ContentVersionArticleCountProjection articleCount(
            String id,
            String lawId,
            int articleCount) {
        return new ContentVersionArticleCountProjection(id, lawId, articleCount);
    }

    private static TaskStatusProjection taskStatus(
            String lawId,
            TaskType type,
            TaskState state) {
        TaskStatusProjection projection = mock(TaskStatusProjection.class);
        when(projection.getLawId()).thenReturn(lawId);
        when(projection.getTaskType()).thenReturn(type);
        when(projection.getTaskState()).thenReturn(state);
        return projection;
    }

    private static DashboardTodoTaskProjection task(
            String taskId,
            String lawId,
            TaskType type,
            TaskState state,
            Instant updatedAt) {
        return new DashboardTodoTaskProjection(
                taskId,
                taskId + "名称",
                type,
                state,
                lawId,
                updatedAt);
    }
}
