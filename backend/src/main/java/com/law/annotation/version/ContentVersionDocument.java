package com.law.annotation.version;

import com.law.annotation.law.ArticleSnapshot;
import com.law.annotation.law.LawDomainRules;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "content_versions")
public class ContentVersionDocument {

    @Id
    private final String id;
    private final String lawId;
    private final int seq;
    private final List<ArticleSnapshot> semanticArticlesSnapshot;
    private final String createdBy;
    private final Instant createdAt;

    public ContentVersionDocument(
            String id,
            String lawId,
            int seq,
            List<ArticleSnapshot> semanticArticlesSnapshot,
            String createdBy,
            Instant createdAt) {
        this.id = LawDomainRules.requireIdentifier(id, "contentVersionId");
        this.lawId = LawDomainRules.requireIdentifier(lawId, "lawId");
        if (seq < 1) {
            throw new IllegalArgumentException("ContentVersion序号必须大于等于1");
        }
        this.seq = seq;
        if (semanticArticlesSnapshot == null) {
            throw new IllegalArgumentException("semanticArticlesSnapshot不能为空");
        }
        validateSnapshotIdentity(semanticArticlesSnapshot);
        this.semanticArticlesSnapshot = List.copyOf(semanticArticlesSnapshot);
        this.createdBy = LawDomainRules.requireIdentifier(createdBy, "createdBy");
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt不能为空");
        }
        this.createdAt = createdAt;
    }

    public static ContentVersionDocument create(
            String lawId,
            int seq,
            List<ArticleSnapshot> semanticArticlesSnapshot,
            String createdBy,
            Instant createdAt) {
        return new ContentVersionDocument(
                UUID.randomUUID().toString(),
                lawId,
                seq,
                semanticArticlesSnapshot,
                createdBy,
                createdAt);
    }

    public String getId() {
        return id;
    }

    public String getLawId() {
        return lawId;
    }

    public int getSeq() {
        return seq;
    }

    public List<ArticleSnapshot> getSemanticArticlesSnapshot() {
        return semanticArticlesSnapshot;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static void validateSnapshotIdentity(List<ArticleSnapshot> articles) {
        Set<String> articleIds = new HashSet<>();
        Set<String> numbers = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (ArticleSnapshot article : articles) {
            if (article == null) {
                throw new IllegalArgumentException("semanticArticlesSnapshot不能包含null");
            }
            if (!articleIds.add(article.getArticleId())) {
                throw new IllegalArgumentException("同一ContentVersion内articleId不能重复");
            }
            if (!numbers.add(article.getNumber())) {
                throw new IllegalArgumentException("同一ContentVersion内条号不能重复");
            }
            if (!orders.add(article.getOrder())) {
                throw new IllegalArgumentException("同一ContentVersion内条文顺序不能重复");
            }
        }
    }
}
