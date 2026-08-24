package com.law.annotation.law;

import com.law.annotation.common.enums.ValidityStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "laws")
public class LawDocument {

    @Id
    private final String id;
    private final String name;
    private final String normalizedName;
    private final String issuingAuthority;
    private final LocalDate publicationDate;
    private final ValidityStatus validityStatus;
    private final List<LawStructureNode> structure;
    private boolean deleted;
    private Instant deletedAt;
    private final String currentContentVersionId;
    private final String currentAnnotationVersionId;
    private final boolean pendingRevision;
    private final PendingChangeSet pendingChangeSet;
    private final Instant createdAt;
    private Instant updatedAt;

    public LawDocument(
            String id,
            String name,
            String normalizedName,
            String issuingAuthority,
            LocalDate publicationDate,
            ValidityStatus validityStatus,
            List<LawStructureNode> structure,
            Instant deletedAt,
            String currentContentVersionId,
            String currentAnnotationVersionId,
            boolean pendingRevision,
            PendingChangeSet pendingChangeSet,
            Instant createdAt,
            Instant updatedAt) {
        this.id = LawDomainRules.requireIdentifier(id, "lawId");
        this.name = LawDomainRules.validateLawName(name);
        String expectedNormalizedName = LawDomainRules.normalizeLawName(this.name);
        if (!expectedNormalizedName.equals(normalizedName)) {
            throw new IllegalArgumentException("normalizedName与name的规范化结果不一致");
        }
        this.normalizedName = normalizedName;
        this.issuingAuthority = LawDomainRules.validateIssuingAuthority(issuingAuthority);
        this.publicationDate = LawDomainRules.requirePublicationDate(publicationDate);
        this.validityStatus = LawDomainRules.requireValidityStatus(validityStatus);
        this.structure = structure == null ? List.of() : List.copyOf(structure);
        this.deletedAt = deletedAt;
        this.deleted = deletedAt != null;
        this.currentContentVersionId = LawDomainRules.requireIdentifier(
                currentContentVersionId, "currentContentVersionId");
        this.currentAnnotationVersionId = currentAnnotationVersionId == null
                ? null
                : LawDomainRules.requireIdentifier(
                        currentAnnotationVersionId, "currentAnnotationVersionId");
        this.pendingRevision = pendingRevision;
        this.pendingChangeSet = pendingChangeSet == null ? PendingChangeSet.empty() : pendingChangeSet;
        if (pendingRevision && this.currentAnnotationVersionId == null) {
            throw new IllegalArgumentException("没有正式标注版本时不能处于待修订状态");
        }
        if (!pendingRevision && !this.pendingChangeSet.isEmpty()) {
            throw new IllegalArgumentException("非待修订法律不能包含pendingChangeSet");
        }
        if (createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("创建和更新时间不能为空");
        }
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static LawDocument createInitial(
            String id,
            String name,
            String issuingAuthority,
            LocalDate publicationDate,
            ValidityStatus validityStatus,
            List<LawStructureNode> structure,
            String currentContentVersionId,
            Instant now) {
        String validName = LawDomainRules.validateLawName(name);
        return new LawDocument(
                id,
                validName,
                LawDomainRules.normalizeLawName(validName),
                issuingAuthority,
                publicationDate,
                validityStatus,
                structure,
                null,
                currentContentVersionId,
                null,
                false,
                PendingChangeSet.empty(),
                now,
                now);
    }

    public void markDeleted(Instant deletedAt) {
        if (deletedAt == null) {
            throw new IllegalArgumentException("删除时间不能为空");
        }
        this.deleted = true;
        this.deletedAt = deletedAt;
        this.updatedAt = deletedAt;
    }

    public boolean isDeleted() {
        return deleted || deletedAt != null;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public String getIssuingAuthority() {
        return issuingAuthority;
    }

    public LocalDate getPublicationDate() {
        return publicationDate;
    }

    public ValidityStatus getValidityStatus() {
        return validityStatus;
    }

    public List<LawStructureNode> getStructure() {
        return structure;
    }

    public Instant getDeletedAt() {
        return deletedAt;
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

    public PendingChangeSet getPendingChangeSet() {
        return pendingChangeSet;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
