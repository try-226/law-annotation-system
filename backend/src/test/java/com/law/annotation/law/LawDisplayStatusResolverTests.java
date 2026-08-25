package com.law.annotation.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.task.TaskRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LawDisplayStatusResolverTests {

    private final TaskRepository taskRepository = org.mockito.Mockito.mock(TaskRepository.class);
    private final LawDisplayStatusResolver resolver = new LawDisplayStatusResolver(taskRepository);

    @Test
    void derivesUnannotatedAndCompletedWithoutTasks() {
        when(taskRepository.findByLawIdInAndTaskStateIn(anyCollection(), anyCollection()))
                .thenReturn(List.of());

        Map<String, LawDisplayStatus> statuses = resolver.resolve(List.of(
                law("law-new", false, null),
                law("law-completed", false, "annotation-1")));

        assertThat(statuses)
                .containsEntry("law-new", LawDisplayStatus.UNANNOTATED)
                .containsEntry("law-completed", LawDisplayStatus.COMPLETED);
    }

    @Test
    void mapsEveryUnfinishedOrdinaryTaskState() {
        List<TaskDocument> tasks = List.of(
                task("task-1", "law-1", TaskType.ORDINARY, TaskState.PENDING_ANNOTATION),
                task("task-2", "law-2", TaskType.ORDINARY, TaskState.ANNOTATING),
                task("task-3", "law-3", TaskType.ORDINARY, TaskState.PENDING_REVIEW),
                task("task-4", "law-4", TaskType.ORDINARY, TaskState.PARTIALLY_REJECTED),
                task("task-5", "law-5", TaskType.ORDINARY, TaskState.PENDING_REREVIEW));
        when(taskRepository.findByLawIdInAndTaskStateIn(anyCollection(), anyCollection()))
                .thenReturn(tasks);

        Map<String, LawDisplayStatus> statuses = resolver.resolve(List.of(
                law("law-1", false, null),
                law("law-2", false, null),
                law("law-3", false, null),
                law("law-4", false, null),
                law("law-5", false, null)));

        assertThat(statuses)
                .containsEntry("law-1", LawDisplayStatus.PENDING_ANNOTATION)
                .containsEntry("law-2", LawDisplayStatus.ANNOTATING)
                .containsEntry("law-3", LawDisplayStatus.PENDING_REVIEW)
                .containsEntry("law-4", LawDisplayStatus.PARTIALLY_REJECTED)
                .containsEntry("law-5", LawDisplayStatus.PENDING_REREVIEW);
    }

    @Test
    void unfinishedOrdinaryTaskOutranksCompletedAnnotationVersion() {
        when(taskRepository.findByLawIdInAndTaskStateIn(anyCollection(), anyCollection()))
                .thenReturn(List.of(task(
                        "task-1", "law-1", TaskType.ORDINARY, TaskState.PENDING_REVIEW)));

        LawDisplayStatus status = resolver.resolve(List.of(
                law("law-1", false, "annotation-1"))).get("law-1");

        assertThat(status).isEqualTo(LawDisplayStatus.PENDING_REVIEW);
    }

    @Test
    void pendingRevisionOutranksCompletedAnnotationVersion() {
        when(taskRepository.findByLawIdInAndTaskStateIn(anyCollection(), anyCollection()))
                .thenReturn(List.of());

        LawDisplayStatus status = resolver.resolve(List.of(
                law("law-1", true, "annotation-1"))).get("law-1");

        assertThat(status).isEqualTo(LawDisplayStatus.PENDING_REVISION);
    }

    @Test
    void unfinishedRevisionTaskOutranksPendingRevision() {
        when(taskRepository.findByLawIdInAndTaskStateIn(anyCollection(), anyCollection()))
                .thenReturn(List.of(task(
                        "task-1", "law-1", TaskType.REVISION, TaskState.PENDING_ANNOTATION)));

        LawDisplayStatus status = resolver.resolve(List.of(
                law("law-1", true, "annotation-1"))).get("law-1");

        assertThat(status).isEqualTo(LawDisplayStatus.REVISING);
    }

    @Test
    void resolvesAWholeLawPageWithOneBatchTaskQuery() {
        when(taskRepository.findByLawIdInAndTaskStateIn(anyCollection(), anyCollection()))
                .thenReturn(List.of());

        resolver.resolve(List.of(
                law("law-1", false, null),
                law("law-2", false, null),
                law("law-3", false, null)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> lawIds = ArgumentCaptor.forClass(List.class);
        verify(taskRepository).findByLawIdInAndTaskStateIn(lawIds.capture(), anyCollection());
        assertThat(lawIds.getValue()).containsExactly("law-1", "law-2", "law-3");
    }

    private static LawDocument law(
            String id,
            boolean pendingRevision,
            String currentAnnotationVersionId) {
        Instant now = Instant.parse("2026-08-25T00:00:00Z");
        return new LawDocument(
                id,
                "测试法" + id,
                "测试法" + id,
                "制定机关",
                LocalDate.of(2026, 8, 25),
                ValidityStatus.ACTIVE,
                List.of(),
                null,
                "content-" + id,
                currentAnnotationVersionId,
                pendingRevision,
                PendingChangeSet.empty(),
                now,
                now);
    }

    private static TaskDocument task(
            String taskId,
            String lawId,
            TaskType taskType,
            TaskState taskState) {
        Instant now = Instant.parse("2026-08-25T00:00:00Z");
        return new TaskDocument(
                taskId,
                taskType,
                taskState,
                lawId,
                "annotator-1",
                "标注员",
                "测试任务",
                null,
                "content-" + lawId,
                null,
                null,
                List.of(),
                null,
                "admin-1",
                null,
                null,
                null,
                null,
                now,
                now);
    }
}
