package com.law.annotation.law;

import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.response.ApiResponse;
import com.law.annotation.common.response.PageResponse;
import com.law.annotation.law.dto.CreateLawArticleRequest;
import com.law.annotation.law.dto.LawDetailResponse;
import com.law.annotation.law.dto.LawDetailViewResponse;
import com.law.annotation.law.dto.LawImportConfirmRequest;
import com.law.annotation.law.dto.LawImportParseRequest;
import com.law.annotation.law.dto.LawImportPreviewResponse;
import com.law.annotation.law.dto.LawListItemResponse;
import com.law.annotation.law.dto.RecycleLawListItemResponse;
import com.law.annotation.law.dto.UpdateLawArticleRequest;
import com.law.annotation.law.dto.UpdateLawBaseRequest;
import com.law.annotation.law.dto.UpdateLawStructureRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/laws")
@PreAuthorize("hasRole('ADMIN')")
public class LawController {

    private final LawImportService lawImportService;
    private final LawQueryService lawQueryService;
    private final LawMaintenanceService lawMaintenanceService;
    private final LawRecycleService lawRecycleService;

    public LawController(
            LawImportService lawImportService,
            LawQueryService lawQueryService,
            LawMaintenanceService lawMaintenanceService,
            LawRecycleService lawRecycleService) {
        this.lawImportService = lawImportService;
        this.lawQueryService = lawQueryService;
        this.lawMaintenanceService = lawMaintenanceService;
        this.lawRecycleService = lawRecycleService;
    }

    @PostMapping("/import/parse")
    public ApiResponse<LawImportPreviewResponse> parseImport(
            @Valid @RequestBody LawImportParseRequest request) {
        return ApiResponse.success(lawImportService.parse(request.fullTextPaste()));
    }

    @PostMapping("/import/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LawDetailResponse> confirmImport(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody LawImportConfirmRequest request) {
        return ApiResponse.success(lawImportService.confirm(request, principal.id()));
    }

    @GetMapping
    public ApiResponse<PageResponse<LawListItemResponse>> listLaws(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(lawQueryService.list(name, page, size));
    }

    @GetMapping("/recycle")
    public ApiResponse<PageResponse<RecycleLawListItemResponse>> listRecycle(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(lawQueryService.listRecycle(name, page, size));
    }

    @GetMapping("/{lawId}")
    public ApiResponse<LawDetailViewResponse> getLaw(@PathVariable String lawId) {
        return ApiResponse.success(lawQueryService.getViewDetail(lawId));
    }

    @PatchMapping("/{lawId}/base")
    public ApiResponse<LawDetailResponse> updateBase(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String lawId,
            @Valid @RequestBody UpdateLawBaseRequest request) {
        return ApiResponse.success(lawMaintenanceService.updateBase(lawId, request, principal.id()));
    }

    @PatchMapping("/{lawId}/structure")
    public ApiResponse<LawDetailResponse> updateStructure(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String lawId,
            @Valid @RequestBody UpdateLawStructureRequest request) {
        return ApiResponse.success(lawMaintenanceService.updateStructure(lawId, request, principal.id()));
    }

    @PostMapping("/{lawId}/articles")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LawDetailResponse> addArticle(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String lawId,
            @Valid @RequestBody CreateLawArticleRequest request) {
        return ApiResponse.success(lawMaintenanceService.addArticle(lawId, request, principal.id()));
    }

    @PatchMapping("/{lawId}/articles/{articleId}")
    public ApiResponse<LawDetailResponse> updateArticle(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String lawId,
            @PathVariable String articleId,
            @Valid @RequestBody UpdateLawArticleRequest request) {
        return ApiResponse.success(
                lawMaintenanceService.updateArticle(lawId, articleId, request, principal.id()));
    }

    @DeleteMapping("/{lawId}/articles/{articleId}")
    public ApiResponse<LawDetailResponse> deleteArticle(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String lawId,
            @PathVariable String articleId) {
        return ApiResponse.success(lawMaintenanceService.deleteArticle(lawId, articleId, principal.id()));
    }

    @DeleteMapping("/{lawId}")
    public ApiResponse<Void> deleteLaw(@PathVariable String lawId) {
        lawRecycleService.deleteLaw(lawId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{lawId}/restore")
    public ApiResponse<LawDetailResponse> restoreLaw(@PathVariable String lawId) {
        return ApiResponse.success(lawRecycleService.restoreLaw(lawId));
    }
}
