package com.law.annotation.field.dto;

import com.law.annotation.field.FieldConfigScope;
import com.law.annotation.field.FieldValueKind;

public record FieldConfigItemResponse(
        String fieldKey,
        String displayName,
        FieldValueKind type,
        FieldConfigScope scope,
        boolean required,
        boolean configurable) {
}
