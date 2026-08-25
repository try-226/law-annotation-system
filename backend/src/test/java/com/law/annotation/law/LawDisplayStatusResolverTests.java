package com.law.annotation.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.task.TaskRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LawDisplayStatusResolverTests {

    @Test
    void derivesUnannotatedWithoutFormalAnnotationActiveTaskOrPendingRevision() {
        assertResolution(
                law("law-1", null, false),
                null,
                LawDisplayStatus.UNANNOTATED,
                false);
    }

    @Test
    void mapsAllUnfinishedOrdinaryTaskStates() {
        LawDocument law = law("law-1", null, false);

        assertResolution(law, task(TaskType.ORDINARY, TaskState.PENDING_ANNOTATION),
                LawDisplayStatus.ANNOTATING, true);
        assertResolution(law, task(TaskType.ORDINARY, TaskState.ANNOTATING),
                LawDisplayStatus.ANNOTATING, true);
        assertResolution(law, task(TaskType.ORDINARY, TaskState.PENDING_REVIEW),
                LawDisplayStatus.PENDING_REVIEW, true);
        assertResolution(law, task(TaskType.ORDINARY, TaskState.PARTIALLY_REJECTED),
                LawDisplayStatus.PARTIALLY_REJECTED, true);
        assertResolution(law, task(TaskType.ORDINARY, TaskState.PENDING_REREVIEW),
                LawDisplayStatus.PENDING_REREVIEW, true);
    }

    @Test
    void derivesCompletedFromFormalAnnotationWithoutActiveTaskOrPendingRevision() {
        assertResolution(
                law("law-1", "annotation-1", false),
                null,
                LawDisplayStatus.COMPLETED,
                false);
    }

    @Test
    void pendingRevisionTakesPriorityOverFormalAnnotationWhenNoTaskIsActive() {
        assertResolution(
                law("law-1", "annotation-1", true),
                null,
                LawDisplayStatus.PENDING_REVISION,
                false);
    }

    @Test
    void unfinishedRevisionTaskIsRevisingAndMaintenanceLocked() {
        assertResolution(
                law("law-1", "annotation-1", true),
                task(TaskType.REVISION, TaskState.PENDING_REVIEW),
                LawDisplayStatus.REVISING,
                true);
    }

    @Test
    void approvedAndCanceledOrdinaryTasksDoNotLockMaintenance() {
        LawDocument law = law("law-1", "annotation-1", false);

        assertResolution(law, task(TaskType.ORDINARY, TaskState.APPROVED),
                LawDisplayStatus.COMPLETED, false);
        assertResolution(law, task(TaskType.ORDINARY, TaskState.CANCELED),
                LawDisplayStatus.COMPLETED, false);
    }

    @Test
    void resolvesWholeLawPageWithOneBatchTaskQuery() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        LawDisplayStatusResolver resolver = new LawDisplayStatusResolver(taskRepository);
        LawDocument first = law("law-1", null, false);
        LawDocument second = law("law-2", "annotation-1", false);
        TaskDocument activeTask = task(TaskType.ORDINARY, TaskState.PENDING_REVIEW);
        when(activeTask.getLawId()).thenReturn("law-1");
        when(taskRepository.findUnfinishedByLawIds(List.of("law-1", "law-2")))
                .thenReturn(List.of(activeTask));

        Map<String, LawDisplayStatusResolver.Resolution> resolutions =
                resolver.resolveAll(List.of(first, second));

        assertThat(resolutions.get("law-1").displayStatus())
                .isEqualTo(LawDisplayStatus.PENDING_REVIEW);
        assertThat(resolutions.get("law-2").displayStatus())
                .isEqualTo(LawDisplayStatus.COMPLETED);
        verify(taskRepository).findUnfinishedByLawIds(List.of("law-1", "law-2"));
    }

    private static void assertResolution(
            LawDocument law,
            TaskDocument task,
            LawDisplayStatus expectedStatus,
            boolean expectedMaintenanceLocked) {
        LawDisplayStatusResolver.Resolution resolution =
                LawDisplayStatusResolver.resolve(law, task);
        assertThat(resolution.displayStatus()).isEqualTo(expectedStatus);
        assertThat(resolution.maintenanceLocked()).isEqualTo(expectedMaintenanceLocked);
    }

    private static LawDocument law(
            String lawId,
            String currentAnnotationVersionId,
            boolean pendingRevision) {
        LawDocument law = mock(LawDocument.class);
        when(law.getId()).thenReturn(lawId);
        when(law.getCurrentAnnotationVersionId()).thenReturn(currentAnnotationVersionId);
        when(law.isPendingRevision()).thenReturn(pendingRevision);
        return law;
    }

    private static TaskDocument task(TaskType taskType, TaskState taskState) {
        TaskDocument task = mock(TaskDocument.class);
        when(task.getTaskType()).thenReturn(taskType);
        when(task.getTaskState()).thenReturn(taskState);
        return task;
    }
}
