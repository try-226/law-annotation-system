package com.law.annotation.law;

import com.law.annotation.law.dto.LawDetailResponse;
import com.law.annotation.law.dto.LawListItemResponse;
import com.law.annotation.law.dto.RecycleLawListItemResponse;
import com.law.annotation.version.ContentVersionDocument;
import java.util.Comparator;

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
}
