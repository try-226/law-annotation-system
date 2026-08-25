package com.law.annotation.task;

import com.law.annotation.user.UserBusinessUsagePort;
import com.law.annotation.review.ReviewRoundRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskUserBusinessUsageAdapter implements UserBusinessUsagePort {

    private final TaskRepository taskRepository;
    private final ReviewRoundRepository reviewRoundRepository;

    @Autowired
    public TaskUserBusinessUsageAdapter(
            TaskRepository taskRepository,
            ReviewRoundRepository reviewRoundRepository) {
        this.taskRepository = taskRepository;
        this.reviewRoundRepository = reviewRoundRepository;
    }

    public TaskUserBusinessUsageAdapter(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
        this.reviewRoundRepository = null;
    }

    @Override
    public boolean hasActiveTask(String userId) {
        return taskRepository.existsByAnnotatorIdAndTaskStateIn(
                userId, TaskStateRules.UNFINISHED_STATES);
    }

    @Override
    public boolean hasUnfinishedReviewRound(String userId) {
        return reviewRoundRepository != null
                && reviewRoundRepository.existsByReviewerIdAndCompletedAtIsNull(userId);
    }

    @Override
    public boolean hasBusinessHistory(String userId) {
        return taskRepository.existsByAnnotatorIdOrCreatedByOrCanceledBy(
                        userId, userId, userId)
                || (reviewRoundRepository != null
                        && reviewRoundRepository.existsByReviewerId(userId));
    }
}
