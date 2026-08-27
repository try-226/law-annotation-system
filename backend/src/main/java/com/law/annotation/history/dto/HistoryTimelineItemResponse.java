package com.law.annotation.history.dto;

import com.law.annotation.history.HistoryCategory;
import com.law.annotation.history.HistoryDetailType;
import com.law.annotation.history.HistoryItemType;
import java.time.Instant;

public record HistoryTimelineItemResponse(
        String eventId,
        HistoryCategory category,
        HistoryItemType type,
        String entityId,
        String taskId,
        String actorId,
        Instant occurredAt,
        String summary,
        DetailRef detailRef) {

    public record DetailRef(HistoryDetailType type, String resourceId) {
    }
}
