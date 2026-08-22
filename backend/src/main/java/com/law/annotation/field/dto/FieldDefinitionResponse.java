package com.law.annotation.field.dto;

import com.law.annotation.field.FieldDefinitionDocument;
import com.law.annotation.field.FieldDefinitionStatus;
import com.law.annotation.field.FieldType;
import java.time.Instant;
import java.util.List;

public record FieldDefinitionResponse(
        String id,
        String name,
        String displayName,
        String description,
        FieldType fieldType,
        boolean required,
        List<String> options,
        FieldDefinitionStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static FieldDefinitionResponse from(FieldDefinitionDocument document) {
        return new FieldDefinitionResponse(
                document.getId(),
                document.getName(),
                document.getDisplayName(),
                document.getDescription(),
                document.getFieldType(),
                document.isRequired(),
                document.getOptions(),
                document.getStatus(),
                document.getCreatedAt(),
                document.getUpdatedAt());
    }
}
