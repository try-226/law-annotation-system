package com.law.annotation.history.dto;

import java.time.Instant;
import java.util.List;

public record LawHistoryResponse(
        String lawId,
        boolean deleted,
        Instant deletedAt,
        List<HistoryTimelineItemResponse> timeline) {

    public LawHistoryResponse {
        timeline = List.copyOf(timeline);
    }
}
