package com.law.annotation.export.formatter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.law.annotation.export.dto.PlainLawExport;
import org.springframework.stereotype.Component;

@Component
public class PlainExportJsonFormatter {

    private final ObjectMapper objectMapper;

    public PlainExportJsonFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] format(PlainLawExport export) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(export);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("纯法律正文JSON序列化失败", exception);
        }
    }
}
