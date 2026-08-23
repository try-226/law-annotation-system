package com.law.annotation.task;

import com.law.annotation.user.UserBusinessUsagePort;
import org.springframework.stereotype.Component;

@Component
public class TaskUserBusinessUsageAdapter implements UserBusinessUsagePort {

    private final TaskRepository taskRepository;

    public TaskUserBusinessUsageAdapter(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public boolean hasActiveTask(String userId) {
        return taskRepository.existsByAnnotatorIdAndTaskStateIn(
                userId, TaskStateRules.UNFINISHED_STATES);
    }

    @Override
    public boolean hasUnfinishedReviewRound(String userId) {
        return false;
    }

    @Override
    public boolean hasBusinessHistory(String userId) {
        return taskRepository.existsByAnnotatorId(userId);
    }
}
