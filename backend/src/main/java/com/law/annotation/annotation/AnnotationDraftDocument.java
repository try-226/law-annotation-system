package com.law.annotation.annotation;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "annotation_drafts")
public class AnnotationDraftDocument {

    @Id
    private String taskId;
    private String annotatorId;
    private OverallAnnotationFields overallFields;
    private Map<String, ArticleAnnotationFields> articleFields;
    private Instant createdAt;
    private Instant updatedAt;

    public AnnotationDraftDocument() {
    }

    public AnnotationDraftDocument(
            String taskId,
            String annotatorId,
            OverallAnnotationFields overallFields,
            Map<String, ArticleAnnotationFields> articleFields,
            Instant createdAt,
            Instant updatedAt) {
        this.taskId = taskId;
        this.annotatorId = annotatorId;
        this.overallFields = overallFields;
        this.articleFields = articleFields == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(articleFields);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getAnnotatorId() {
        return annotatorId;
    }

    public OverallAnnotationFields getOverallFields() {
        return overallFields;
    }

    public Map<String, ArticleAnnotationFields> getArticleFields() {
        return articleFields == null ? Map.of() : Map.copyOf(articleFields);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
