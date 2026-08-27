package com.law.annotation.export.formatter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.law.annotation.export.dto.FormalLawExport;
import org.springframework.stereotype.Component;

@Component
public class FormalExportJsonFormatter {

    private final ObjectMapper objectMapper;

    public FormalExportJsonFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] format(FormalLawExport export) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(export);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("正式标注JSON序列化失败", exception);
        }
    }
}
