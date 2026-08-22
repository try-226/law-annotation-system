package com.law.annotation.field;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "field_definitions")
public class FieldDefinitionDocument {

    @Id
    private String id;
    private String name;
    private String displayName;
    private String description;
    private FieldType fieldType;
    private boolean required;
    private List<String> options;
    private FieldDefinitionStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public FieldDefinitionDocument() {
    }

    public FieldDefinitionDocument(
            String name,
            String displayName,
            String description,
            FieldType fieldType,
            boolean required,
            List<String> options,
            FieldDefinitionStatus status,
            Instant createdAt,
            Instant updatedAt) {
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.fieldType = fieldType;
        this.required = required;
        this.options = List.copyOf(options);
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public FieldType getFieldType() {
        return fieldType;
    }

    public boolean isRequired() {
        return required;
    }

    public List<String> getOptions() {
        return List.copyOf(options);
    }

    public FieldDefinitionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(
            String displayName,
            String description,
            boolean required,
            List<String> options,
            FieldDefinitionStatus status,
            Instant updatedAt) {
        this.displayName = displayName;
        this.description = description;
        this.required = required;
        this.options = List.copyOf(options);
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public void deactivate(Instant updatedAt) {
        this.status = FieldDefinitionStatus.INACTIVE;
        this.updatedAt = updatedAt;
    }
}
