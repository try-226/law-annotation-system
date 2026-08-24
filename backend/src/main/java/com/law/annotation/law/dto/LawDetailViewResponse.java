package com.law.annotation.law.dto;

import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.law.LawStructureNodeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record LawDetailViewResponse(
        String id,
        String name,
        String issuingAuthority,
        LocalDate publicationDate,
        ValidityStatus validityStatus,
        Instant updatedAt,
        List<StructureNode> structure,
        List<Article> articles,
        CurrentTask currentTask,
        boolean hasActiveTask,
        boolean pendingRevision,
        AnnotationVersionReference currentAnnotationVersion,
        String currentContentVersionId,
        int currentContentVersionSeq,
        ContentVersionReference currentContentVersion,
        boolean hasHistory,
        Instant createdAt) {

    public LawDetailViewResponse {
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

    public record Article(
            String articleId,
            String number,
            String body,
            int order,
            List<String> chapterPath) {

        public Article {
            chapterPath = List.copyOf(chapterPath);
        }
    }

    public record CurrentTask(
            String taskId,
            TaskType taskType,
            TaskState taskState,
            String taskName,
            String annotatorId,
            String annotatorName) {
    }

    public record AnnotationVersionReference(String id) {
    }

    public record ContentVersionReference(
            String id,
            int seq,
            Instant createdAt) {
    }
}
