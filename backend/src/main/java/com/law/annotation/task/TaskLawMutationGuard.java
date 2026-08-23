package com.law.annotation.task;

import com.law.annotation.common.exception.ApiException;
import com.law.annotation.law.LawMutationGuard;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class TaskLawMutationGuard implements LawMutationGuard {

    private final TaskRepository taskRepository;

    public TaskLawMutationGuard(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public void assertMutationAllowed(String lawId) {
        if (taskRepository.existsByLawIdAndTaskStateIn(
                lawId, TaskStateRules.UNFINISHED_STATES)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "LAW.ACTIVE_TASK_EXISTS",
                    "法律存在未结束任务，不能修改");
        }
    }
}
