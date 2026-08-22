package com.law.annotation.field;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "field_config")
public class FieldConfigDocument {

    @Id
    private String id;
    private String fieldKey;
    private boolean required;
    private String updatedBy;
    private Instant updatedAt;

    public FieldConfigDocument() {
    }

    public FieldConfigDocument(
            String fieldKey,
            boolean required,
            String updatedBy,
            Instant updatedAt) {
        this.fieldKey = fieldKey;
        this.required = required;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public boolean isRequired() {
        return required;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateRequired(boolean required, String updatedBy, Instant updatedAt) {
        this.required = required;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }
}
