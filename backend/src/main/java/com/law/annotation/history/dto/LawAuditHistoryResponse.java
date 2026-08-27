package com.law.annotation.history.dto;

import com.law.annotation.law.LawAuditType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record LawAuditHistoryResponse(
        String auditId,
        String lawId,
        LawAuditType auditType,
        Map<String, Object> before,
        Map<String, Object> after,
        String operatorId,
        Instant operatedAt) {

    public LawAuditHistoryResponse {
        before = before == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(before));
        after = after == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(after));
    }
}
