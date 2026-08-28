package com.law.annotation.law;

import org.springframework.data.annotation.Id;

/** Lightweight law view containing only fields required by the admin dashboard. */
public record LawDashboardProjection(
        @Id String id,
        String name,
        String currentContentVersionId,
        String currentAnnotationVersionId,
        boolean pendingRevision) {

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCurrentContentVersionId() {
        return currentContentVersionId;
    }

    public String getCurrentAnnotationVersionId() {
        return currentAnnotationVersionId;
    }

    public boolean isPendingRevision() {
        return pendingRevision;
    }
}
