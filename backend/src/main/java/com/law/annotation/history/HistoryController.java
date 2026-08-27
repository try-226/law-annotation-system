package com.law.annotation.history;

import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.response.ApiResponse;
import com.law.annotation.history.dto.AnnotationVersionHistoryResponse;
import com.law.annotation.history.dto.ContentVersionHistoryResponse;
import com.law.annotation.history.dto.LawAuditHistoryResponse;
import com.law.annotation.history.dto.LawHistoryResponse;
import com.law.annotation.history.dto.TaskHistoryResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/laws/{lawId}/history")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public ApiResponse<LawHistoryResponse> history(
            @PathVariable String lawId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(historyService.getLawHistory(lawId, principal));
    }

    @GetMapping("/content-versions/{contentVersionId}")
    public ApiResponse<ContentVersionHistoryResponse> contentVersion(
            @PathVariable String lawId,
            @PathVariable String contentVersionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(historyService.getContentVersion(lawId, contentVersionId, principal));
    }

    @GetMapping("/annotation-versions/{annotationVersionId}")
    public ApiResponse<AnnotationVersionHistoryResponse> annotationVersion(
            @PathVariable String lawId,
            @PathVariable String annotationVersionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(historyService.getAnnotationVersion(lawId, annotationVersionId, principal));
    }

    @GetMapping("/audits/{auditId}")
    public ApiResponse<LawAuditHistoryResponse> audit(
            @PathVariable String lawId,
            @PathVariable String auditId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(historyService.getAudit(lawId, auditId, principal));
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<TaskHistoryResponse> task(
            @PathVariable String lawId,
            @PathVariable String taskId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(historyService.getTask(lawId, taskId, principal));
    }
}
