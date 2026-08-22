package com.law.annotation.field.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.Map;

public class UpdateFieldConfigRequest {

    @NotBlank(message = "字段键不能为空")
    private String fieldKey;

    @NotNull(message = "必填设置不能为空")
    private Boolean required;

    @JsonIgnore
    private final Map<String, Object> unsupportedFields = new LinkedHashMap<>();

    public UpdateFieldConfigRequest() {
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public void setFieldKey(String fieldKey) {
        this.fieldKey = fieldKey;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    @JsonAnySetter
    public void captureUnsupportedField(String name, Object value) {
        unsupportedFields.put(name, value);
    }

    @JsonIgnore
    @AssertTrue(message = "请求只允许包含fieldKey和required")
    public boolean isSupportedShape() {
        return unsupportedFields.isEmpty();
    }
}
