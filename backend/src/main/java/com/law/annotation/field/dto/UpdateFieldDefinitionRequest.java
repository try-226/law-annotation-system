package com.law.annotation.field.dto;

import com.law.annotation.field.FieldDefinitionStatus;
import com.law.annotation.field.FieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import java.util.List;

public record UpdateFieldDefinitionRequest(
        @Null(message = "系统字段名称不可修改") String name,
        @Null(message = "字段类型不可修改") FieldType fieldType,
        @NotBlank(message = "显示名称不能为空") String displayName,
        String description,
        boolean required,
        List<String> options,
        @NotNull(message = "字段状态不能为空") FieldDefinitionStatus status) {
}
