package com.law.annotation.field.dto;

import java.util.List;

public record FieldConfigResponse(List<FieldConfigItemResponse> fields) {

    public FieldConfigResponse {
        fields = List.copyOf(fields);
    }
}
