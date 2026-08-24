package com.law.annotation.law;

import com.law.annotation.common.exception.ApiException;
import com.law.annotation.common.response.ErrorLocator;
import com.law.annotation.law.dto.LawBaseInfoInput;
import com.law.annotation.law.dto.LawDetailResponse;
import com.law.annotation.law.dto.UpdateLawArticleInput;
import com.law.annotation.law.dto.UpdateLawRequest;
import com.law.annotation.version.ContentVersionDocument;
import com.law.annotation.version.ContentVersionRepository;
import com.mongodb.client.result.UpdateResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LawUpdateService {

    private final LawRepository lawRepository;
    private final ContentVersionRepository contentVersionRepository;
    private final LawAuditRepository lawAuditRepository;
    private final LawQueryService lawQueryService;
    private final MongoTemplate mongoTemplate;
    private final List<LawMutationGuard> mutationGuards;
    private final LawOperationCoordinator operationCoordinator;

    public LawUpdateService(
            LawRepository lawRepository,
            ContentVersionRepository contentVersionRepository,
            LawAuditRepository lawAuditRepository,
            LawQueryService lawQueryService,
            MongoTemplate mongoTemplate,
            List<LawMutationGuard> mutationGuards,
            LawOperationCoordinator operationCoordinator) {
        this.lawRepository = lawRepository;
        this.contentVersionRepository = contentVersionRepository;
        this.lawAuditRepository = lawAuditRepository;
        this.lawQueryService = lawQueryService;
        this.mongoTemplate = mongoTemplate;
        this.mutationGuards = List.copyOf(mutationGuards);
        this.operationCoordinator = operationCoordinator;
    }

    public LawDetailResponse updateLaw(
            String lawId,
            UpdateLawRequest request,
            String operatorId) {
        lawQueryService.requireVisibleLaw(lawId);
        return operationCoordinator.withVisibleLaw(
                lawId,
                LawUpdateService::versionConflict,
                operationToken -> updateLawLocked(
                        lawId,
                        request,
                        operatorId,
                        operationToken));
    }

    private LawDetailResponse updateLawLocked(
            String lawId,
            UpdateLawRequest request,
            String operatorId,
            String operationToken) {
        LawDocument law = lawQueryService.requireVisibleLaw(lawId);
        mutationGuards.forEach(guard -> guard.assertMutationAllowed(lawId));
        ContentVersionDocument current = lawQueryService.requireCurrentVersion(law);
        if (request == null) {
            throw validation("law", "法律更新内容不能为空");
        }

        ValidatedBase base = validateBase(request.baseInfo());
        lawRepository.findFirstByNormalizedNameAndIdNot(base.normalizedName(), lawId)
                .ifPresent(conflict -> {
                    throw nameConflict();
                });
        ValidatedArticles articleResult = validateArticles(
                current.getSemanticArticlesSnapshot(),
                request.articles());
        List<LawStructureNode> structure = validateStructure(
                request,
                articleResult.articleRefs());

        boolean baseChanged = !baseSnapshot(law).equals(base.snapshot());
        boolean structureChanged = !structureSnapshot(law.getStructure())
                .equals(structureSnapshot(structure));
        boolean semanticChanged = !semanticArticlesEqual(
                current.getSemanticArticlesSnapshot(),
                articleResult.articles());
        if (!baseChanged && !structureChanged && !semanticChanged) {
            return lawQueryService.getDetail(lawId);
        }

        Instant now = Instant.now();
        ContentVersionDocument next = semanticChanged
                ? createNextVersion(law, current, articleResult.articles(), operatorId, now)
                : null;
        List<LawAuditDocument> audits = new ArrayList<>();
        if (baseChanged) {
            audits.add(LawAuditDocument.create(
                    lawId,
                    LawAuditType.BASE_INFO,
                    baseSnapshot(law),
                    base.snapshot(),
                    operatorId,
                    now));
        }
        if (structureChanged) {
            audits.add(LawAuditDocument.create(
                    lawId,
                    LawAuditType.STRUCTURE,
                    Map.of("structure", structureSnapshot(law.getStructure())),
                    Map.of("structure", structureSnapshot(structure)),
                    operatorId,
                    now));
        }

        List<String> insertedAuditIds = new ArrayList<>();
        boolean contentVersionInserted = false;
        try {
            if (next != null) {
                contentVersionRepository.insert(next);
                contentVersionInserted = true;
            }
            for (LawAuditDocument audit : audits) {
                lawAuditRepository.insert(audit);
                insertedAuditIds.add(audit.getId());
            }
            Update update = new Update().set("updatedAt", now);
            if (baseChanged) {
                update.set("name", base.name())
                        .set("normalizedName", base.normalizedName())
                        .set("issuingAuthority", base.issuingAuthority())
                        .set("publicationDate", base.publicationDate())
                        .set("validityStatus", base.validityStatus());
            }
            if (structureChanged) {
                update.set("structure", structure);
            }
            if (next != null) {
                boolean pendingRevision = law.getCurrentAnnotationVersionId() != null;
                update.set("currentContentVersionId", next.getId())
                        .set("pendingRevision", pendingRevision)
                        .set("pendingChangeSet", pendingRevision
                                ? accumulatePendingChanges(law.getPendingChangeSet(), articleResult)
                                : PendingChangeSet.empty());
            }
            UpdateResult result = mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id")
                            .is(lawId)
                            .and("deletedAt").is(null)
                            .and("updatedAt").is(law.getUpdatedAt())
                            .and("currentContentVersionId").is(current.getId())
                            .and(LawOperationCoordinator.OPERATION_TOKEN_FIELD).is(operationToken)),
                    update,
                    LawDocument.class);
            if (result.getModifiedCount() != 1) {
                throw versionConflict();
            }
        } catch (DuplicateKeyException exception) {
            compensate(next, insertedAuditIds, exception);
            if (next != null && !contentVersionInserted) {
                throw versionConflict();
            }
            if (baseChanged) {
                throw nameConflict();
            }
            throw versionConflict();
        } catch (RuntimeException exception) {
            compensate(next, insertedAuditIds, exception);
            throw exception;
        }
        return lawQueryService.getDetail(lawId);
    }

    private static ValidatedBase validateBase(LawBaseInfoInput input) {
        if (input == null) {
            throw validation("baseInfo", "法律基础信息不能为空");
        }
        try {
            String name = LawDomainRules.validateLawName(input.name());
            String normalizedName = LawDomainRules.normalizeLawName(name);
            String authority = LawDomainRules.validateIssuingAuthority(input.issuingAuthority());
            return new ValidatedBase(
                    name,
                    normalizedName,
                    authority,
                    LawDomainRules.requirePublicationDate(input.publicationDate()),
                    LawDomainRules.requireValidityStatus(input.validityStatus()));
        } catch (IllegalArgumentException exception) {
            throw validation("baseInfo", exception.getMessage());
        }
    }

    private static ValidatedArticles validateArticles(
            List<ArticleSnapshot> currentArticles,
            List<UpdateLawArticleInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    LawErrorCodes.LAST_ARTICLE_REQUIRED,
                    "法律至少需要保留一条法条");
        }
        Map<String, ArticleSnapshot> remaining = new LinkedHashMap<>();
        currentArticles.forEach(article -> remaining.put(article.getArticleId(), article));
        Map<String, String> articleRefs = new HashMap<>();
        List<ArticleSnapshot> articles = new ArrayList<>();
        Set<String> added = new LinkedHashSet<>();
        Set<String> modified = new LinkedHashSet<>();
        try {
            for (UpdateLawArticleInput input : inputs) {
                if (input == null) {
                    throw new IllegalArgumentException("articles不能包含null");
                }
                String clientKey = LawDomainRules.requireIdentifier(input.clientKey(), "articles.clientKey");
                ArticleSnapshot article;
                if (input.articleId() == null || input.articleId().isBlank()) {
                    article = ArticleSnapshot.createNew(input.number(), input.body(), input.order());
                    added.add(article.getArticleId());
                } else {
                    ArticleSnapshot previous = remaining.remove(input.articleId());
                    if (previous == null) {
                        throw new IllegalArgumentException("articleId不存在或重复提交");
                    }
                    article = ArticleSnapshot.carryForward(
                            previous.getArticleId(),
                            input.number(),
                            input.body(),
                            input.order());
                    if (!articleEqual(previous, article)) {
                        modified.add(article.getArticleId());
                    }
                }
                if (articleRefs.putIfAbsent(clientKey, article.getArticleId()) != null) {
                    throw new IllegalArgumentException("articles.clientKey不能重复");
                }
                articles.add(article);
            }
            articles = validatedArticles(articles);
        } catch (IllegalArgumentException exception) {
            throw validation("articles", exception.getMessage());
        }
        return new ValidatedArticles(
                articles,
                articleRefs,
                added,
                modified,
                new LinkedHashSet<>(remaining.keySet()));
    }

    private static List<LawStructureNode> validateStructure(
            UpdateLawRequest request,
            Map<String, String> articleRefs) {
        if (request.structure() == null) {
            throw validation("structure", "结构不能为空");
        }
        try {
            List<LawStructureNode> structure = LawImportService.toStructure(
                    request.structure(),
                    articleRefs);
            return LawStructureValidator.validate(structure, articleRefs.values());
        } catch (IllegalArgumentException exception) {
            throw validation("structure", exception.getMessage());
        }
    }

    private static ContentVersionDocument createNextVersion(
            LawDocument law,
            ContentVersionDocument current,
            List<ArticleSnapshot> articles,
            String operatorId,
            Instant now) {
        try {
            return ContentVersionDocument.create(
                    law.getId(),
                    current.getSeq() + 1,
                    articles,
                    operatorId,
                    now);
        } catch (IllegalArgumentException exception) {
            throw validation("articles", exception.getMessage());
        }
    }

    private static PendingChangeSet accumulatePendingChanges(
            PendingChangeSet current,
            ValidatedArticles changes) {
        PendingChangeSet accumulated = current;
        for (String articleId : changes.deletedArticleIds()) {
            accumulated = accumulated.recordDeletion(articleId);
        }
        for (String articleId : changes.addedArticleIds()) {
            accumulated = accumulated.recordAddition(articleId);
        }
        for (String articleId : changes.modifiedArticleIds()) {
            accumulated = accumulated.recordModification(articleId);
        }
        return accumulated;
    }

    private void compensate(
            ContentVersionDocument contentVersion,
            List<String> auditIds,
            RuntimeException originalFailure) {
        for (String auditId : auditIds) {
            try {
                mongoTemplate.remove(
                        Query.query(Criteria.where("_id").is(auditId)),
                        LawAuditDocument.class);
            } catch (RuntimeException compensationFailure) {
                originalFailure.addSuppressed(compensationFailure);
            }
        }
        if (contentVersion != null) {
            try {
                mongoTemplate.remove(
                        Query.query(Criteria.where("_id").is(contentVersion.getId())),
                        ContentVersionDocument.class);
            } catch (RuntimeException compensationFailure) {
                originalFailure.addSuppressed(compensationFailure);
            }
        }
    }

    private static List<ArticleSnapshot> validatedArticles(List<ArticleSnapshot> articles) {
        List<ArticleSnapshot> sorted = articles.stream()
                .sorted(Comparator.comparingInt(ArticleSnapshot::getOrder))
                .toList();
        new ContentVersionDocument(
                "validation-only",
                "validation-only",
                1,
                sorted,
                "validation-only",
                Instant.EPOCH);
        return sorted;
    }

    private static boolean semanticArticlesEqual(
            List<ArticleSnapshot> current,
            List<ArticleSnapshot> updated) {
        if (current.size() != updated.size()) {
            return false;
        }
        List<ArticleSnapshot> sortedCurrent = current.stream()
                .sorted(Comparator.comparingInt(ArticleSnapshot::getOrder))
                .toList();
        List<ArticleSnapshot> sortedUpdated = updated.stream()
                .sorted(Comparator.comparingInt(ArticleSnapshot::getOrder))
                .toList();
        for (int index = 0; index < sortedCurrent.size(); index++) {
            if (!articleEqual(sortedCurrent.get(index), sortedUpdated.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean articleEqual(ArticleSnapshot first, ArticleSnapshot second) {
        return first.getArticleId().equals(second.getArticleId())
                && first.getNumber().equals(second.getNumber())
                && first.getBody().equals(second.getBody())
                && first.getOrder() == second.getOrder();
    }

    private static Map<String, Object> baseSnapshot(LawDocument law) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", law.getName());
        snapshot.put("issuingAuthority", law.getIssuingAuthority());
        snapshot.put("publicationDate", law.getPublicationDate());
        snapshot.put("validityStatus", law.getValidityStatus());
        return snapshot;
    }

    private static List<Map<String, Object>> structureSnapshot(List<LawStructureNode> structure) {
        return structure.stream()
                .sorted(Comparator.comparingInt(LawStructureNode::getOrder))
                .map(node -> {
                    Map<String, Object> snapshot = new LinkedHashMap<>();
                    snapshot.put("nodeId", node.getNodeId());
                    snapshot.put("type", node.getType());
                    snapshot.put("title", node.getTitle());
                    snapshot.put("parentNodeId", node.getParentNodeId());
                    snapshot.put("order", node.getOrder());
                    snapshot.put("articleIds", node.getArticleIds());
                    return snapshot;
                })
                .toList();
    }

    private static ApiException validation(String path, String message) {
        return new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                LawErrorCodes.VALIDATION_FAILED,
                "法律数据校验失败",
                List.of(new ErrorLocator(path, message)));
    }

    private static ApiException nameConflict() {
        return new ApiException(
                HttpStatus.CONFLICT,
                LawErrorCodes.NAME_ALREADY_EXISTS,
                "法律名称已存在");
    }

    private static ApiException versionConflict() {
        return new ApiException(
                HttpStatus.CONFLICT,
                LawErrorCodes.VERSION_CONFLICT,
                "法律内容已发生变化，请刷新后重试");
    }

    private record ValidatedBase(
            String name,
            String normalizedName,
            String issuingAuthority,
            java.time.LocalDate publicationDate,
            com.law.annotation.common.enums.ValidityStatus validityStatus) {

        Map<String, Object> snapshot() {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("name", name);
            snapshot.put("issuingAuthority", issuingAuthority);
            snapshot.put("publicationDate", publicationDate);
            snapshot.put("validityStatus", validityStatus);
            return snapshot;
        }
    }

    private record ValidatedArticles(
            List<ArticleSnapshot> articles,
            Map<String, String> articleRefs,
            Set<String> addedArticleIds,
            Set<String> modifiedArticleIds,
            Set<String> deletedArticleIds) {
    }
}
