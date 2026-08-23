package com.law.annotation.task;

import com.law.annotation.auth.AuthErrorCodes;
import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.common.response.ApiResponse;
import com.law.annotation.common.response.PageResponse;
import com.law.annotation.task.dto.CancelTaskRequest;
import com.law.annotation.task.dto.CreateOrdinaryTaskRequest;
import com.law.annotation.task.dto.TaskDetailResponse;
import com.law.annotation.task.dto.TaskListItemResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public ApiResponse<PageResponse<TaskListItemResponse>> list(
            @RequestParam(required = false) String taskName,
            @RequestParam(required = false) TaskType taskType,
            @RequestParam(required = false) String lawId,
            @RequestParam(required = false) String annotatorId,
            @RequestParam(required = false) TaskState state,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireRole(principal, Role.ADMIN);
        return ApiResponse.success(taskService.list(
                taskName, taskType, lawId, annotatorId, state, page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TaskDetailResponse> create(
            @Valid @RequestBody CreateOrdinaryTaskRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireRole(principal, Role.ADMIN);
        return ApiResponse.success(taskService.createOrdinaryTask(
                request.getLawId(),
                request.getAnnotatorId(),
                request.getTaskName(),
                request.getRemark(),
                principal.id()));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<TaskDetailResponse> detail(
            @PathVariable String taskId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(taskService.getDetail(taskId, principal));
    }

    @PostMapping("/{taskId}/start")
    public ApiResponse<TaskDetailResponse> start(
            @PathVariable String taskId,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireRole(principal, Role.ANNOTATOR);
        return ApiResponse.success(taskService.start(taskId, principal.id()));
    }

    @PostMapping("/{taskId}/cancel")
    public ApiResponse<TaskDetailResponse> cancel(
            @PathVariable String taskId,
            @Valid @RequestBody CancelTaskRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(taskService.cancel(taskId, request.reason(), principal));
    }

    private static void requireRole(UserPrincipal principal, Role expectedRole) {
        if (principal == null || principal.role() != expectedRole) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    AuthErrorCodes.FORBIDDEN,
                    "无权执行此操作");
        }
    }
}
