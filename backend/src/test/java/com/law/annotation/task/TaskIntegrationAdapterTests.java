package com.law.annotation.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.law.annotation.common.exception.ApiException;
import com.law.annotation.law.LawErrorCodes;
import com.law.annotation.review.ReviewRoundRepository;
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
                .isEqualTo(LawErrorCodes.ACTIVE_TASK_EXISTS);
    }

    @Test
    void userUsagePortIncludesReviewAssignmentAndHistory() {
        ReviewRoundRepository reviewRoundRepository =
                org.mockito.Mockito.mock(ReviewRoundRepository.class);
        when(taskRepository.existsByAnnotatorIdAndTaskStateIn(
                "annotator-1", TaskStateRules.UNFINISHED_STATES)).thenReturn(true);
        when(taskRepository.existsByAnnotatorIdOrCreatedByOrCanceledBy(
                "annotator-1", "annotator-1", "annotator-1")).thenReturn(true);
        when(reviewRoundRepository.existsByReviewerIdAndCompletedAtIsNull("annotator-1"))
                .thenReturn(true);
        when(reviewRoundRepository.existsByReviewerId("annotator-1")).thenReturn(true);
        TaskUserBusinessUsageAdapter adapter = new TaskUserBusinessUsageAdapter(
                taskRepository, reviewRoundRepository);

        assertThat(adapter.hasActiveTask("annotator-1")).isTrue();
        assertThat(adapter.hasBusinessHistory("annotator-1")).isTrue();
        assertThat(adapter.hasUnfinishedReviewRound("annotator-1")).isTrue();
    }
}
