package com.law.annotation.annotation.dto;

import com.law.annotation.common.enums.TaskState;
import java.time.Instant;

public record SubmitReviewResponse(
        String taskId,
        String submissionId,
        TaskState taskState,
        Instant submittedAt) {
}
