package com.law.annotation.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.task.TaskStatusProjection;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class LawDisplayStatusResolverTests {

    private final LawDisplayStatusResolver resolver = new LawDisplayStatusResolver();

    @Test
    void exposesOnlyTheEightFrozenDisplayStatuses() {
        assertThat(Arrays.stream(LawDisplayStatus.values()).map(Enum::name))
                .containsExactly(
                        "UNANNOTATED",
                        "ANNOTATING",
                        "PENDING_REVIEW",
                        "PARTIALLY_REJECTED",
                        "PENDING_REREVIEW",
                        "PENDING_REVISION",
                        "REVISING",
                        "COMPLETED");
    }

    @Test
    void mapsEveryOrdinaryUnfinishedStateToItsOrdinaryDisplayStatus() {
        assertThat(resolver.resolveActiveTask(task(TaskType.ORDINARY, TaskState.PENDING_ANNOTATION)))
                .isEqualTo(LawDisplayStatus.ANNOTATING);
        assertThat(resolver.resolveActiveTask(task(TaskType.ORDINARY, TaskState.ANNOTATING)))
                .isEqualTo(LawDisplayStatus.ANNOTATING);
        assertThat(resolver.resolveActiveTask(task(TaskType.ORDINARY, TaskState.PENDING_REVIEW)))
                .isEqualTo(LawDisplayStatus.PENDING_REVIEW);
        assertThat(resolver.resolveActiveTask(task(TaskType.ORDINARY, TaskState.PARTIALLY_REJECTED)))
                .isEqualTo(LawDisplayStatus.PARTIALLY_REJECTED);
        assertThat(resolver.resolveActiveTask(task(TaskType.ORDINARY, TaskState.PENDING_REREVIEW)))
                .isEqualTo(LawDisplayStatus.PENDING_REREVIEW);
    }

    @Test
    void mapsEveryRevisionUnfinishedStateToRevising() {
        for (TaskState state : TaskStateRulesTestData.UNFINISHED_STATES) {
            assertThat(resolver.resolveActiveTask(task(TaskType.REVISION, state)))
                    .isEqualTo(LawDisplayStatus.REVISING);
        }
    }

    @Test
    void rejectsFinishedTasksForBothTaskTypes() {
        for (TaskType taskType : TaskType.values()) {
            for (TaskState state : List.of(TaskState.APPROVED, TaskState.CANCELED)) {
                assertThatThrownBy(() -> resolver.resolveActiveTask(task(taskType, state)))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("仅未结束任务可用于计算法律展示状态");
            }
        }
    }

    @Test
    void fallsBackFromPendingRevisionToCompletedAndThenUnannotated() {
        assertThat(resolver.resolve(law(true, "annotation-1"), null))
                .isEqualTo(LawDisplayStatus.PENDING_REVISION);
        assertThat(resolver.resolve(law(false, "annotation-1"), null))
                .isEqualTo(LawDisplayStatus.COMPLETED);
        assertThat(resolver.resolve(law(false, null), null))
                .isEqualTo(LawDisplayStatus.UNANNOTATED);
    }

    @Test
    void activeTaskHasPriorityOverPendingRevisionAndFormalAnnotation() {
        LawDocument law = law(true, "annotation-1");

        assertThat(resolver.resolve(
                law,
                task(TaskType.REVISION, TaskState.PENDING_ANNOTATION)))
                .isEqualTo(LawDisplayStatus.REVISING);
    }

    private static TaskStatusProjection task(TaskType type, TaskState state) {
        TaskStatusProjection task = mock(TaskStatusProjection.class);
        when(task.getTaskType()).thenReturn(type);
        when(task.getTaskState()).thenReturn(state);
        return task;
    }

    private static LawDocument law(boolean pendingRevision, String annotationVersionId) {
        LawDocument law = mock(LawDocument.class);
        when(law.isPendingRevision()).thenReturn(pendingRevision);
        when(law.getCurrentAnnotationVersionId()).thenReturn(annotationVersionId);
        return law;
    }

    private static final class TaskStateRulesTestData {
        private static final List<TaskState> UNFINISHED_STATES = List.of(
                TaskState.PENDING_ANNOTATION,
                TaskState.ANNOTATING,
                TaskState.PENDING_REVIEW,
                TaskState.PARTIALLY_REJECTED,
                TaskState.PENDING_REREVIEW);

        private TaskStateRulesTestData() {
        }
    }
}
