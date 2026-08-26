package com.law.annotation.export.dto;

import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.law.LawStructureNodeType;
import java.time.LocalDate;
import java.util.List;

public record PlainLawExport(
        LawInfo law,
        List<StructureNode> structure,
        List<Article> articles) {

    public PlainLawExport {
        structure = List.copyOf(structure);
        articles = List.copyOf(articles);
    }

    public record LawInfo(
            String lawId,
            String name,
            String issuingAuthority,
            LocalDate publicationDate,
            ValidityStatus validityStatus,
            String currentContentVersionId,
            int currentContentVersionSeq) {
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
            String number,
            String body,
            int order,
            List<String> structurePath) {

        public Article {
            structurePath = List.copyOf(structurePath);
        }
    }
}
