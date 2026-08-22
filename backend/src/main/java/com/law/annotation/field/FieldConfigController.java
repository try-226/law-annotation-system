package com.law.annotation.field;

import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.response.ApiResponse;
import com.law.annotation.field.dto.FieldConfigResponse;
import com.law.annotation.field.dto.UpdateFieldConfigRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/field-config")
public class FieldConfigController {

    private final FieldConfigService fieldConfigService;

    public FieldConfigController(FieldConfigService fieldConfigService) {
        this.fieldConfigService = fieldConfigService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<FieldConfigResponse> getCurrentConfig(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(fieldConfigService.getCurrentConfig(principal.role()));
    }

    @PatchMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<FieldConfigResponse> updateRequired(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateFieldConfigRequest request) {
        return ApiResponse.success(fieldConfigService.updateRequired(
                request.getFieldKey(),
                request.getRequired(),
                principal.id(),
                principal.role()));
    }
}
