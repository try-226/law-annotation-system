package com.law.annotation.review.dto;

import com.law.annotation.common.enums.TaskState;
import com.law.annotation.field.FieldConfigSnapshot;
import com.law.annotation.review.ReviewRoundOutcome;
import com.law.annotation.review.ReviewRoundType;
import com.law.annotation.task.TaskContentVersionSnapshot;
import com.law.annotation.task.TaskLawBaseInfoSnapshot;
import com.law.annotation.task.TaskStructureNodeSnapshot;
import java.time.Instant;
import java.util.List;

public record ReviewDetailResponse(
        String taskId,
        String reviewRoundId,
        int roundNo,
        ReviewRoundType roundType,
        TaskState taskState,
        String reviewerId,
        boolean writable,
        ReviewProgressResponse progress,
        List<ReviewItemResponse> items,
        TaskContentVersionSnapshot contentVersionSnapshot,
        TaskLawBaseInfoSnapshot lawBaseInfoSnapshot,
        List<TaskStructureNodeSnapshot> structureSnapshot,
        FieldConfigSnapshot fieldConfigSnapshot,
        ReviewSubmissionSnapshotResponse before,
        ReviewSubmissionSnapshotResponse after,
        ReviewRoundOutcome outcome,
        String annotationVersionId,
        Instant startedAt,
        Instant completionStartedAt,
        Instant completedAt) {

    public ReviewDetailResponse {
        items = List.copyOf(items);
        structureSnapshot = List.copyOf(structureSnapshot);
    }
}
