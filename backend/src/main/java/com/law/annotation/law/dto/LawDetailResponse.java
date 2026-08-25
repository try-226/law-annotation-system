package com.law.annotation.law.dto;

import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.law.LawStructureNodeType;
import com.law.annotation.law.LawDisplayStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record LawDetailResponse(
        String id,
        String name,
        String issuingAuthority,
        LocalDate publicationDate,
        ValidityStatus validityStatus,
        List<StructureNode> structure,
        List<Article> articles,
        String currentContentVersionId,
        int currentContentVersionSeq,
        boolean pendingRevision,
        LawDisplayStatus displayStatus,
        Instant createdAt,
        Instant updatedAt) {

    public LawDetailResponse {
        structure = List.copyOf(structure);
        articles = List.copyOf(articles);
    }

    public record StructureNode(
            String nodeId,
            LawStructureNodeType type,
            String title,
            String parentNodeId,
            int order,
            List<String> articleIds) {

        public StructureNode {
            articleIds = List.copyOf(articleIds);
        }
    }

    public record Article(String articleId, String number, String body, int order) {
    }
}
