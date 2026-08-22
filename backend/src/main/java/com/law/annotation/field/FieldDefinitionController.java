package com.law.annotation.field;

import com.law.annotation.common.response.ApiResponse;
import com.law.annotation.common.response.PageResponse;
import com.law.annotation.field.dto.CreateFieldDefinitionRequest;
import com.law.annotation.field.dto.FieldDefinitionResponse;
import com.law.annotation.field.dto.UpdateFieldDefinitionRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/field-definitions")
@PreAuthorize("hasRole('ADMIN')")
public class FieldDefinitionController {

    private final FieldDefinitionService service;

    public FieldDefinitionController(FieldDefinitionService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<FieldDefinitionResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.list(page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FieldDefinitionResponse> create(
            @Valid @RequestBody CreateFieldDefinitionRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<FieldDefinitionResponse> get(@PathVariable String id) {
        return ApiResponse.success(service.get(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<FieldDefinitionResponse> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateFieldDefinitionRequest request) {
        return ApiResponse.success(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<FieldDefinitionResponse> deactivate(@PathVariable String id) {
        return ApiResponse.success(service.deactivate(id));
    }
}
