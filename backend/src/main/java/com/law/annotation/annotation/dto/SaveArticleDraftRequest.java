package com.law.annotation.annotation.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import java.util.LinkedHashMap;
import java.util.Map;

public class SaveArticleDraftRequest {

    private String itemType;
    private String keywords;
    private String subjects;
    private String legalLiability;
    private String annotationNote;

    @JsonIgnore
    private final Map<String, Object> unsupportedFields = new LinkedHashMap<>();

    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public String getSubjects() {
        return subjects;
    }

    public void setSubjects(String subjects) {
        this.subjects = subjects;
    }

    public String getLegalLiability() {
        return legalLiability;
    }

    public void setLegalLiability(String legalLiability) {
        this.legalLiability = legalLiability;
    }

    public String getAnnotationNote() {
        return annotationNote;
    }

    public void setAnnotationNote(String annotationNote) {
        this.annotationNote = annotationNote;
    }

    @JsonAnySetter
    public void captureUnsupportedField(String name, Object value) {
        unsupportedFields.put(name, value);
    }

    @JsonIgnore
    @AssertTrue(message = "法条草稿包含未定义字段")
    public boolean isSupportedShape() {
        return unsupportedFields.isEmpty();
    }
}
