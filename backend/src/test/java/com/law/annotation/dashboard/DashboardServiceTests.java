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
import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.dashboard.dto.DashboardSummaryResponse;
import com.law.annotation.dashboard.dto.DashboardTodoResponse;
import com.law.annotation.field.FieldConfigSnapshot;
import com.law.annotation.law.ArticleSnapshot;
import com.law.annotation.law.LawDisplayStatusResolver;
import com.law.annotation.law.LawDocument;
import com.law.annotation.law.LawRepository;
import com.law.annotation.law.PendingChangeSet;
import com.law.annotation.task.TaskContentVersionSnapshot;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.task.TaskLawBaseInfoSnapshot;
import com.law.annotation.task.TaskRepository;
import com.law.annotation.task.TaskStateRules;
import com.law.annotation.task.TaskStatusProjection;
import com.law.annotation.version.ContentVersionDocument;
import com.law.annotation.version.ContentVersionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        LawDocument unannotated = law("law-unannotated", "c-unannotated", null, false);
        LawDocument annotating = law("law-annotating", "c-annotating", null, false);
        LawDocument completed = law("law-completed", "c-completed", "a-completed", false);
        LawDocument pending = law("law-pending", "c-pending", "a-pending", true);
        LawDocument revising = law("law-revising", "c-revising", "a-revising", true);
        List<LawDocument> laws = List.of(
                unannotated, annotating, completed, pending, revising);
        when(lawRepository.findAllByDeletedAtIsNull()).thenReturn(laws);
        when(contentVersionRepository.findByIdIn(List.of(
                        "c-unannotated",
                        "c-annotating",
                        "c-completed",
                        "c-pending",
                        "c-revising")))
                .thenReturn(List.of(
                        version("c-unannotated", "law-unannotated", 2),
                        version("c-annotating", "law-annotating", 1),
                        version("c-completed", "law-completed", 3),
                        version("c-pending", "law-pending", 4),
                        version("c-revising", "law-revising", 5)));
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
                        laws.stream().map(LawDocument::getId).toList()))
                .thenReturn(2L);
        when(taskRepository.countByTaskStateAndLawIdIn(
                        TaskState.PENDING_REVIEW,
                        laws.stream().map(LawDocument::getId).toList()))
                .thenReturn(1L);
        when(taskRepository.countByTaskStateAndLawIdIn(
                        TaskState.PENDING_REREVIEW,
                        laws.stream().map(LawDocument::getId).toList()))
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
    }

    @Test
    void summaryDelegatesActiveAndReviewCountsToPreciseDatabaseQueries() {
        LawDocument law = law("law-1", "c-1", null, false);
        when(lawRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(law));
        when(contentVersionRepository.findByIdIn(List.of("c-1")))
                .thenReturn(List.of(version("c-1", "law-1", 1)));
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
        LawDocument firstLaw = law("law-1", "c-1", null, false);
        LawDocument secondLaw = law("law-2", "c-2", null, false);
        when(lawRepository.findAllByDeletedAtIsNull())
                .thenReturn(List.of(firstLaw, secondLaw));
        TaskDocument review = task(
                "task-review", firstLaw, TaskType.ORDINARY, TaskState.PENDING_REVIEW, T0);
        TaskDocument rereview = task(
                "task-rereview",
                secondLaw,
                TaskType.REVISION,
                TaskState.PENDING_REREVIEW,
                T0.plusSeconds(1));
        TaskDocument deletedLawTask = task(
                "task-deleted",
                law("deleted-law", "c-deleted", null, false),
                TaskType.ORDINARY,
                TaskState.PENDING_REVIEW,
                T0.plusSeconds(2));
        when(taskRepository.findTop10ByTaskStateAndLawIdInOrderByCreatedAtDescTaskIdDesc(
                        TaskState.PENDING_REVIEW, List.of("law-1", "law-2")))
                .thenReturn(List.of(deletedLawTask, review));
        when(taskRepository.findTop10ByTaskStateAndLawIdInOrderByCreatedAtDescTaskIdDesc(
                        TaskState.PENDING_REREVIEW, List.of("law-1", "law-2")))
                .thenReturn(List.of(rereview));

        DashboardTodoResponse result = service.getTodos();

        assertThat(result.pendingReview()).singleElement().satisfies(item -> {
            assertThat(item.taskId()).isEqualTo("task-review");
            assertThat(item.lawId()).isEqualTo("law-1");
            assertThat(item.lawName()).isEqualTo("law-1名称");
            assertThat(item.taskState()).isEqualTo(TaskState.PENDING_REVIEW);
        });
        assertThat(result.pendingRereview()).singleElement().satisfies(item -> {
            assertThat(item.taskId()).isEqualTo("task-rereview");
            assertThat(item.taskType()).isEqualTo(TaskType.REVISION);
            assertThat(item.taskState()).isEqualTo(TaskState.PENDING_REREVIEW);
        });
    }

    @Test
    void emptyVisibleLawSetReturnsZerosWithoutTaskOrVersionQueries() {
        when(lawRepository.findAllByDeletedAtIsNull()).thenReturn(List.of());

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
        LawDocument law = law("law-1", "c-current", null, false);
        when(lawRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(law));
        when(contentVersionRepository.findByIdIn(List.of("c-current")))
                .thenReturn(List.of(version("c-current", "other-law", 1)));
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

    private static LawDocument law(
            String id,
            String contentVersionId,
            String annotationVersionId,
            boolean pendingRevision) {
        return new LawDocument(
                id,
                id + "名称",
                id + "名称",
                "制定机关",
                LocalDate.of(2026, 8, 27),
                ValidityStatus.ACTIVE,
                List.of(),
                null,
                contentVersionId,
                annotationVersionId,
                pendingRevision,
                pendingRevision
                        ? new PendingChangeSet(Set.of(), Set.of("article-1"), Set.of())
                        : PendingChangeSet.empty(),
                T0,
                T0);
    }

    private static ContentVersionDocument version(
            String id,
            String lawId,
            int articleCount) {
        List<ArticleSnapshot> articles = new ArrayList<>();
        for (int index = 0; index < articleCount; index++) {
            articles.add(new ArticleSnapshot(
                    "article-" + index,
                    "第" + (index + 1) + "条",
                    "正文" + index,
                    index));
        }
        return new ContentVersionDocument(id, lawId, 1, articles, "admin", T0);
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

    private static TaskDocument task(
            String taskId,
            LawDocument law,
            TaskType type,
            TaskState state,
            Instant createdAt) {
        return new TaskDocument(
                taskId,
                type,
                state,
                law.getId(),
                "annotator",
                "标注员",
                taskId + "名称",
                null,
                law.getCurrentContentVersionId(),
                new TaskContentVersionSnapshot(
                        law.getCurrentContentVersionId(), 1, List.of()),
                TaskLawBaseInfoSnapshot.from(law),
                List.of(),
                new FieldConfigSnapshot(List.of(), List.of()),
                "admin",
                null,
                null,
                null,
                null,
                createdAt,
                createdAt);
    }
}
