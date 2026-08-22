package com.law.annotation.field.dto;

import com.law.annotation.field.FieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateFieldDefinitionRequest(
        @NotBlank(message = "系统字段名称不能为空") String name,
        @NotBlank(message = "显示名称不能为空") String displayName,
        String description,
        @NotNull(message = "字段类型不能为空") FieldType fieldType,
        boolean required,
        List<String> options) {
}
