package com.law.annotation.revision.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CreateRevisionTaskRequest {

    @NotBlank(message = "法律ID不能为空")
    private String lawId;

    @NotBlank(message = "标注员ID不能为空")
    private String annotatorId;

    private String taskName;
    private String remark;
    private boolean overall;
    private List<String> articleIds = List.of();

    @JsonIgnore
    private final Map<String, Object> unsupportedFields = new LinkedHashMap<>();

    public String getLawId() {
        return lawId;
    }

    public void setLawId(String lawId) {
        this.lawId = lawId;
    }

    public String getAnnotatorId() {
        return annotatorId;
    }

    public void setAnnotatorId(String annotatorId) {
        this.annotatorId = annotatorId;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public boolean isOverall() {
        return overall;
    }

    public void setOverall(boolean overall) {
        this.overall = overall;
    }

    public List<String> getArticleIds() {
        return articleIds == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(articleIds));
    }

    public void setArticleIds(List<String> articleIds) {
        this.articleIds = articleIds;
    }

    @JsonAnySetter
    public void captureUnsupportedField(String name, Object value) {
        unsupportedFields.put(name, value);
    }

    @JsonIgnore
    @AssertTrue(message = "修订任务请求包含未定义字段")
    public boolean isSupportedShape() {
        return unsupportedFields.isEmpty();
    }
}
