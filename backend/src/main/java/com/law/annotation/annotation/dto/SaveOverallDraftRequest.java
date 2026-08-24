package com.law.annotation.annotation.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import java.util.LinkedHashMap;
import java.util.Map;

public class SaveOverallDraftRequest {

    private String lawCategory;
    private String overallKeywords;
    private String summary;
    private String overallNote;

    @JsonIgnore
    private final Map<String, Object> unsupportedFields = new LinkedHashMap<>();

    public String getLawCategory() {
        return lawCategory;
    }

    public void setLawCategory(String lawCategory) {
        this.lawCategory = lawCategory;
    }

    public String getOverallKeywords() {
        return overallKeywords;
    }

    public void setOverallKeywords(String overallKeywords) {
        this.overallKeywords = overallKeywords;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getOverallNote() {
        return overallNote;
    }

    public void setOverallNote(String overallNote) {
        this.overallNote = overallNote;
    }

    @JsonAnySetter
    public void captureUnsupportedField(String name, Object value) {
        unsupportedFields.put(name, value);
    }

    @JsonIgnore
    @AssertTrue(message = "整体草稿包含未定义字段")
    public boolean isSupportedShape() {
        return unsupportedFields.isEmpty();
    }
}
