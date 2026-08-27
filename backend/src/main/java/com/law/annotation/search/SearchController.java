package com.law.annotation.search;

import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.response.ApiResponse;
import com.law.annotation.common.response.PageResponse;
import com.law.annotation.search.dto.SearchHitResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/laws/search")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<SearchHitResponse>> searchLaws(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "ALL") SearchScope scope,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(searchService.searchLaws(q, scope, page, size));
    }

    @GetMapping("/tasks/{taskId}/search")
    public ApiResponse<PageResponse<SearchHitResponse>> searchTask(
            @PathVariable String taskId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "ALL") SearchScope scope,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                searchService.searchTask(taskId, q, scope, page, size, principal));
    }
}
