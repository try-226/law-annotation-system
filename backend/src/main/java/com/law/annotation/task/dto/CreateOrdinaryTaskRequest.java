package com.law.annotation.task.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;

public class CreateOrdinaryTaskRequest {

    @NotBlank(message = "法律ID不能为空")
    private String lawId;

    @NotBlank(message = "标注员ID不能为空")
    private String annotatorId;

    private String taskName;
    private String remark;

    @JsonIgnore
    private final Map<String, Object> unsupportedFields = new LinkedHashMap<>();

    public CreateOrdinaryTaskRequest() {
    }

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

    @JsonAnySetter
    public void captureUnsupportedField(String name, Object value) {
        unsupportedFields.put(name, value);
    }

    @JsonIgnore
    @AssertTrue(message = "普通任务只允许整部法律，不接受articleIds或其他未定义字段")
    public boolean isSupportedShape() {
        return unsupportedFields.isEmpty();
    }
}
