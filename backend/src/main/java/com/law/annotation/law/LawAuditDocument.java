package com.law.annotation.law;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "law_audits")
public class LawAuditDocument {

    @Id
    private final String id;
    private final String lawId;
    private final LawAuditType auditType;
    private final Map<String, Object> before;
    private final Map<String, Object> after;
    private final String operatorId;
    private final Instant operatedAt;

    public LawAuditDocument(
            String id,
            String lawId,
            LawAuditType auditType,
            Map<String, Object> before,
            Map<String, Object> after,
            String operatorId,
            Instant operatedAt) {
        this.id = LawDomainRules.requireIdentifier(id, "auditId");
        this.lawId = LawDomainRules.requireIdentifier(lawId, "lawId");
        if (auditType == null) {
            throw new IllegalArgumentException("auditType不能为空");
        }
        this.auditType = auditType;
        this.before = immutableMap(before);
        this.after = immutableMap(after);
        this.operatorId = LawDomainRules.requireIdentifier(operatorId, "operatorId");
        if (operatedAt == null) {
            throw new IllegalArgumentException("operatedAt不能为空");
        }
        this.operatedAt = operatedAt;
    }

    public static LawAuditDocument create(
            String lawId,
            LawAuditType auditType,
            Map<String, Object> before,
            Map<String, Object> after,
            String operatorId,
            Instant operatedAt) {
        return new LawAuditDocument(
                UUID.randomUUID().toString(),
                lawId,
                auditType,
                before,
                after,
                operatorId,
                operatedAt);
    }

    public String getId() {
        return id;
    }

    public String getLawId() {
        return lawId;
    }

    public LawAuditType getAuditType() {
        return auditType;
    }

    public Map<String, Object> getBefore() {
        return before;
    }

    public Map<String, Object> getAfter() {
        return after;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public Instant getOperatedAt() {
        return operatedAt;
    }

    private static Map<String, Object> immutableMap(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
