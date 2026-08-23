package com.law.annotation.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.law.annotation.common.exception.ApiException;
import org.junit.jupiter.api.Test;

class TaskIntegrationAdapterTests {

    private final TaskRepository taskRepository = org.mockito.Mockito.mock(TaskRepository.class);

    @Test
    void activeTaskBlocksLawMutationThroughExistingGuardPort() {
        when(taskRepository.existsByLawIdAndTaskStateIn(
                "law-1", TaskStateRules.UNFINISHED_STATES)).thenReturn(true);
        TaskLawMutationGuard guard = new TaskLawMutationGuard(taskRepository);

        assertThatThrownBy(() -> guard.assertMutationAllowed("law-1"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("LAW.ACTIVE_TASK_EXISTS");
    }

    @Test
    void userUsagePortUsesTaskDataWithoutReviewImplementation() {
        when(taskRepository.existsByAnnotatorIdAndTaskStateIn(
                "annotator-1", TaskStateRules.UNFINISHED_STATES)).thenReturn(true);
        when(taskRepository.existsByAnnotatorId("annotator-1")).thenReturn(true);
        TaskUserBusinessUsageAdapter adapter = new TaskUserBusinessUsageAdapter(taskRepository);

        assertThat(adapter.hasActiveTask("annotator-1")).isTrue();
        assertThat(adapter.hasBusinessHistory("annotator-1")).isTrue();
        assertThat(adapter.hasUnfinishedReviewRound("annotator-1")).isFalse();
    }
}
