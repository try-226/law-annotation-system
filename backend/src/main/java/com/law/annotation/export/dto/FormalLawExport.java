package com.law.annotation.export.dto;

import com.law.annotation.common.enums.ItemType;
import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.law.LawStructureNodeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record FormalLawExport(
        LawInfo law,
        SemanticVersion semanticVersion,
        AnnotationVersion annotationVersion,
        OverallAnnotation overallAnnotation,
        List<StructureNode> structure,
        List<Article> articles,
        ApprovalMetadata approvalMetadata) {

    public FormalLawExport {
        structure = List.copyOf(structure);
        articles = List.copyOf(articles);
    }

    public record LawInfo(
            String lawId,
            String name,
            String issuingAuthority,
            LocalDate publicationDate,
            ValidityStatus validityStatus) {
    }

    public record SemanticVersion(String contentVersionId, int contentVersionSeq) {
    }

    public record AnnotationVersion(String annotationVersionId, int annotationVersionSeq) {
    }

    public record OverallAnnotation(
            String lawCategory,
            String overallKeywords,
            String summary,
            String overallNote) {
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

    public record Article(
            String articleId,
            List<String> structurePath,
            String number,
            String body,
            int order,
            ItemType itemType,
            String keywords,
            String subjects,
            String legalLiability,
            String annotationNote) {

        public Article {
            structurePath = List.copyOf(structurePath);
        }
    }

    public record ApprovalMetadata(
            String approvedBy,
            Instant approvedAt,
            String sourceTaskId) {
    }
}
