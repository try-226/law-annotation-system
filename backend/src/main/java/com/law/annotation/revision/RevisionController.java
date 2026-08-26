package com.law.annotation.revision;

import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.response.ApiResponse;
import com.law.annotation.revision.dto.CreateRevisionTaskRequest;
import com.law.annotation.task.dto.TaskDetailResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tasks")
public class RevisionController {

    private final RevisionService revisionService;

    public RevisionController(RevisionService revisionService) {
        this.revisionService = revisionService;
    }

    @PostMapping("/revision")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TaskDetailResponse> create(
            @Valid @RequestBody CreateRevisionTaskRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(revisionService.create(request, principal));
    }
}
