package com.law.annotation.bootstrap;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "bootstrap_seed_states")
public class BootstrapSeedStateDocument {

    @Id
    private final String marker;
    private final String resourceId;
    private final Instant createdAt;

    public BootstrapSeedStateDocument(String marker, String resourceId, Instant createdAt) {
        if (marker == null || marker.isBlank()) {
            throw new IllegalArgumentException("marker不能为空");
        }
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId不能为空");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt不能为空");
        }
        this.marker = marker;
        this.resourceId = resourceId;
        this.createdAt = createdAt;
    }

    public String getMarker() {
        return marker;
    }

    public String getResourceId() {
        return resourceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
