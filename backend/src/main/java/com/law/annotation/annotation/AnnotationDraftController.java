package com.law.annotation.annotation;

import com.law.annotation.annotation.dto.SaveArticleDraftRequest;
import com.law.annotation.annotation.dto.SaveOverallDraftRequest;
import com.law.annotation.annotation.dto.SubmitReviewResponse;
import com.law.annotation.annotation.dto.TaskDraftResponse;
import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tasks/{taskId}")
public class AnnotationDraftController {

    private final AnnotationDraftService annotationDraftService;

    public AnnotationDraftController(AnnotationDraftService annotationDraftService) {
        this.annotationDraftService = annotationDraftService;
    }

    @GetMapping("/draft")
    public ApiResponse<TaskDraftResponse> getDraft(
            @PathVariable String taskId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(annotationDraftService.getDraft(taskId, principal));
    }

    @PutMapping("/draft/overall")
    public ApiResponse<TaskDraftResponse> saveOverall(
            @PathVariable String taskId,
            @Valid @RequestBody SaveOverallDraftRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(annotationDraftService.saveOverall(taskId, request, principal));
    }

    @DeleteMapping("/draft/overall")
    public ApiResponse<TaskDraftResponse> clearOverall(
            @PathVariable String taskId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(annotationDraftService.clearOverall(taskId, principal));
    }

    @PutMapping("/draft/articles/{articleId}")
    public ApiResponse<TaskDraftResponse> saveArticle(
            @PathVariable String taskId,
            @PathVariable String articleId,
            @Valid @RequestBody SaveArticleDraftRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(annotationDraftService.saveArticle(
                taskId, articleId, request, principal));
    }

    @DeleteMapping("/draft/articles/{articleId}")
    public ApiResponse<TaskDraftResponse> clearArticle(
            @PathVariable String taskId,
            @PathVariable String articleId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(annotationDraftService.clearArticle(
                taskId, articleId, principal));
    }

    @PostMapping("/submit-review")
    public ApiResponse<SubmitReviewResponse> submitReview(
            @PathVariable String taskId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(annotationDraftService.submitReview(taskId, principal));
    }
}
