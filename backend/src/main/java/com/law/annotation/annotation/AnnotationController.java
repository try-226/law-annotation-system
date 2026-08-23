package com.law.annotation.annotation;

import com.law.annotation.annotation.dto.AnnotationDraftResponse;
import com.law.annotation.annotation.dto.AnnotationWorkbenchResponse;
import com.law.annotation.annotation.dto.SaveArticleDraftRequest;
import com.law.annotation.annotation.dto.SaveOverallDraftRequest;
import com.law.annotation.auth.AuthErrorCodes;
import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.common.response.ApiResponse;
import com.law.annotation.task.dto.TaskDetailResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tasks/{taskId}")
public class AnnotationController {

    private final AnnotationDraftService annotationDraftService;

    public AnnotationController(AnnotationDraftService annotationDraftService) {
        this.annotationDraftService = annotationDraftService;
    }

    @GetMapping("/annotation")
    public ApiResponse<AnnotationWorkbenchResponse> getWorkbench(
            @PathVariable String taskId,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireAnnotator(principal);
        return ApiResponse.success(annotationDraftService.getWorkbench(taskId, principal));
    }

    @PutMapping("/draft/overall")
    public ApiResponse<AnnotationDraftResponse> saveOverall(
            @PathVariable String taskId,
            @RequestBody SaveOverallDraftRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireAnnotator(principal);
        return ApiResponse.success(
                annotationDraftService.saveOverall(taskId, request, principal));
    }

    @PutMapping("/draft/articles/{articleId}")
    public ApiResponse<AnnotationDraftResponse> saveArticle(
            @PathVariable String taskId,
            @PathVariable String articleId,
            @RequestBody SaveArticleDraftRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireAnnotator(principal);
        return ApiResponse.success(
                annotationDraftService.saveArticle(taskId, articleId, request, principal));
    }

    @PostMapping("/submit-review")
    public ApiResponse<TaskDetailResponse> submitReview(
            @PathVariable String taskId,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireAnnotator(principal);
        return ApiResponse.success(annotationDraftService.submitReview(taskId, principal));
    }

    private static void requireAnnotator(UserPrincipal principal) {
        if (principal == null || principal.role() != Role.ANNOTATOR) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    AuthErrorCodes.FORBIDDEN,
                    "无权执行此操作");
        }
    }
}
