package com.law.annotation.law;

import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.version.ContentVersionDocument;
import com.law.annotation.version.ContentVersionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LawCreationService {

    private final LawRepository lawRepository;
    private final ContentVersionRepository contentVersionRepository;
    private final MongoTemplate mongoTemplate;

    public LawCreationService(
            LawRepository lawRepository,
            ContentVersionRepository contentVersionRepository,
            MongoTemplate mongoTemplate) {
        this.lawRepository = lawRepository;
        this.contentVersionRepository = contentVersionRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public InitialLawCreation createInitialLaw(
            String name,
            String issuingAuthority,
            LocalDate publicationDate,
            ValidityStatus validityStatus,
            List<LawStructureNode> structure,
            List<NewArticleDraft> articles,
            String createdBy) {
        String validName = LawDomainRules.validateLawName(name);
        String normalizedName = LawDomainRules.normalizeLawName(validName);
        if (lawRepository.existsByNormalizedName(normalizedName)) {
            throw nameConflict();
        }
        if (articles == null || articles.isEmpty()) {
            throw new IllegalArgumentException("首次创建法律至少需要一条法条");
        }

        List<ArticleSnapshot> articleSnapshots = articles.stream()
                .map(article -> {
                    if (article == null) {
                        throw new IllegalArgumentException("articles不能包含null");
                    }
                    return new ArticleSnapshot(
                            article.articleId(), article.number(), article.body(), article.order());
                })
                .toList();
        List<LawStructureNode> validStructure = LawStructureValidator.validate(
                structure,
                articleSnapshots.stream().map(ArticleSnapshot::getArticleId).toList());
        Instant now = Instant.now();
        String lawId = UUID.randomUUID().toString();
        String contentVersionId = UUID.randomUUID().toString();
        ContentVersionDocument contentVersion = new ContentVersionDocument(
                contentVersionId,
                lawId,
                1,
                articleSnapshots,
                createdBy,
                now);
        LawDocument law = LawDocument.createInitial(
                lawId,
                validName,
                issuingAuthority,
                publicationDate,
                validityStatus,
                validStructure,
                contentVersionId,
                now);

        contentVersionRepository.insert(contentVersion);
        try {
            lawRepository.insert(law);
        } catch (DuplicateKeyException exception) {
            compensateContentVersion(contentVersionId, exception);
            throw nameConflict();
        } catch (RuntimeException exception) {
            compensateContentVersion(contentVersionId, exception);
            throw exception;
        }
        return new InitialLawCreation(law, contentVersion);
    }

    private void compensateContentVersion(String contentVersionId, RuntimeException originalFailure) {
        try {
            mongoTemplate.remove(
                    Query.query(Criteria.where("_id").is(contentVersionId)),
                    ContentVersionDocument.class);
        } catch (RuntimeException compensationFailure) {
            originalFailure.addSuppressed(compensationFailure);
        }
    }

    private static ApiException nameConflict() {
        return new ApiException(
                HttpStatus.CONFLICT,
                LawErrorCodes.NAME_ALREADY_EXISTS,
                "法律名称已存在");
    }
}
