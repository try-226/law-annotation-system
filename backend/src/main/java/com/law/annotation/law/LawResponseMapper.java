package com.law.annotation.law;

import com.law.annotation.law.dto.LawDetailResponse;
import com.law.annotation.law.dto.LawDetailViewResponse;
import com.law.annotation.law.dto.LawListItemResponse;
import com.law.annotation.law.dto.RecycleLawListItemResponse;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.version.ContentVersionDocument;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class LawResponseMapper {

    private LawResponseMapper() {
    }

    static LawListItemResponse toListItem(LawDocument law, ContentVersionDocument version) {
        return new LawListItemResponse(
                law.getId(),
                law.getName(),
                law.getIssuingAuthority(),
                law.getPublicationDate(),
                law.getValidityStatus(),
                version.getSemanticArticlesSnapshot().size(),
                law.getUpdatedAt());
    }

    static LawDetailResponse toDetail(LawDocument law, ContentVersionDocument version) {
        return new LawDetailResponse(
                law.getId(),
                law.getName(),
                law.getIssuingAuthority(),
                law.getPublicationDate(),
                law.getValidityStatus(),
                law.getStructure().stream()
                        .sorted(Comparator.comparingInt(LawStructureNode::getOrder))
                        .map(node -> new LawDetailResponse.StructureNode(
                                node.getNodeId(),
                                node.getType(),
                                node.getTitle(),
                                node.getParentNodeId(),
                                node.getOrder(),
                                node.getArticleIds()))
                        .toList(),
                version.getSemanticArticlesSnapshot().stream()
                        .sorted(Comparator.comparingInt(ArticleSnapshot::getOrder))
                        .map(article -> new LawDetailResponse.Article(
                                article.getArticleId(),
                                article.getNumber(),
                                article.getBody(),
                                article.getOrder()))
                        .toList(),
                version.getId(),
                version.getSeq(),
                law.isPendingRevision(),
                law.getCreatedAt(),
                law.getUpdatedAt());
    }

    static LawDetailViewResponse toViewDetail(
            LawDocument law,
            ContentVersionDocument version,
            TaskDocument currentTask,
            boolean hasHistory) {
        List<LawStructureNode> sortedStructure = law.getStructure().stream()
                .sorted(Comparator.comparingInt(LawStructureNode::getOrder))
                .toList();
        Map<String, List<String>> chapterPaths = chapterPaths(sortedStructure);
        LawDetailViewResponse.CurrentTask taskResponse = currentTask == null
                ? null
                : new LawDetailViewResponse.CurrentTask(
                        currentTask.getTaskId(),
                        currentTask.getTaskType(),
                        currentTask.getTaskState(),
                        currentTask.getTaskName(),
                        currentTask.getAnnotatorId(),
                        currentTask.getAnnotatorNameSnapshot());
        LawDetailViewResponse.AnnotationVersionReference annotationVersion =
                law.getCurrentAnnotationVersionId() == null
                        ? null
                        : new LawDetailViewResponse.AnnotationVersionReference(
                                law.getCurrentAnnotationVersionId());
        return new LawDetailViewResponse(
                law.getId(),
                law.getName(),
                law.getIssuingAuthority(),
                law.getPublicationDate(),
                law.getValidityStatus(),
                law.getUpdatedAt(),
                sortedStructure.stream()
                        .map(node -> new LawDetailViewResponse.StructureNode(
                                node.getNodeId(),
                                node.getType(),
                                node.getTitle(),
                                node.getParentNodeId(),
                                node.getOrder(),
                                node.getArticleIds()))
                        .toList(),
                version.getSemanticArticlesSnapshot().stream()
                        .sorted(Comparator.comparingInt(ArticleSnapshot::getOrder))
                        .map(article -> new LawDetailViewResponse.Article(
                                article.getArticleId(),
                                article.getNumber(),
                                article.getBody(),
                                article.getOrder(),
                                chapterPaths.getOrDefault(article.getArticleId(), List.of())))
                        .toList(),
                taskResponse,
                currentTask != null,
                law.isPendingRevision(),
                annotationVersion,
                version.getId(),
                version.getSeq(),
                new LawDetailViewResponse.ContentVersionReference(
                        version.getId(),
                        version.getSeq(),
                        version.getCreatedAt()),
                hasHistory,
                law.getCreatedAt());
    }

    static RecycleLawListItemResponse toRecycleListItem(
            LawDocument law,
            ContentVersionDocument version) {
        return new RecycleLawListItemResponse(
                law.getId(),
                law.getName(),
                law.getIssuingAuthority(),
                law.getPublicationDate(),
                law.getValidityStatus(),
                version.getSemanticArticlesSnapshot().size(),
                law.isPendingRevision(),
                law.getDeletedAt(),
                law.getUpdatedAt());
    }

    private static Map<String, List<String>> chapterPaths(List<LawStructureNode> structure) {
        Map<String, LawStructureNode> nodesById = new HashMap<>();
        for (LawStructureNode node : structure) {
            nodesById.put(node.getNodeId(), node);
        }
        Map<String, List<String>> pathsByArticleId = new HashMap<>();
        for (LawStructureNode node : structure) {
            List<String> path = pathTo(node, nodesById);
            for (String articleId : node.getArticleIds()) {
                pathsByArticleId.put(articleId, path);
            }
        }
        return pathsByArticleId;
    }

    private static List<String> pathTo(
            LawStructureNode node,
            Map<String, LawStructureNode> nodesById) {
        ArrayDeque<String> titles = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        LawStructureNode current = node;
        while (current != null && visited.add(current.getNodeId())) {
            titles.addFirst(current.getTitle());
            String parentNodeId = current.getParentNodeId();
            current = parentNodeId == null || parentNodeId.isBlank()
                    ? null
                    : nodesById.get(parentNodeId);
        }
        return List.copyOf(titles);
    }
}
