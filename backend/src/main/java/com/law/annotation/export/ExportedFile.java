package com.law.annotation.export;

import java.util.Objects;
import org.springframework.http.MediaType;

public record ExportedFile(byte[] content, MediaType contentType, String filename) {

    public ExportedFile {
        content = Objects.requireNonNull(content, "content must not be null").clone();
        contentType = Objects.requireNonNull(contentType, "contentType must not be null");
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
