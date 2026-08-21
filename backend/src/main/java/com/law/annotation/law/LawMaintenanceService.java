package com.law.annotation.law;

import com.law.annotation.common.exception.ApiException;
import com.law.annotation.common.response.ErrorLocator;
import com.law.annotation.law.dto.CreateLawArticleRequest;
import com.law.annotation.law.dto.LawDetailResponse;
import com.law.annotation.law.dto.UpdateLawArticleRequest;
import com.law.annotation.law.dto.UpdateLawBaseRequest;
import com.law.annotation.law.dto.UpdateLawStructureRequest;
import com.law.annotation.version.ContentVersionDocument;
import com.law.annotation.version.ContentVersionRepository;
import com.mongodb.client.result.UpdateResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LawMaintenanceService {

    private final LawRepository lawRepository;
    private final ContentVersionRepository contentVersionRepository;
    private final LawAuditRepository lawAuditRepository;
    private final LawQueryService lawQueryService;
    private final MongoTemplate mongoTemplate;
    private final List<LawMutationGuard> mutationGuards;

    public LawMaintenanceService(
            LawRepository lawRepository,
            ContentVersionRepository contentVersionRepository,
            LawAuditRepository lawAuditRepository,
            LawQueryService lawQueryService,
            MongoTemplate mongoTemplate,
            List<LawMutationGuard> mutationGuards) {
        this.lawRepository = lawRepository;
        this.contentVersionRepository = contentVersionRepository;
        this.lawAuditRepository = lawAuditRepository;
        this.lawQueryService = lawQueryService;
        this.mongoTemplate = mongoTemplate;
        this.mutationGuards = List.copyOf(mutationGuards);
    }

    public LawDetailResponse updateBase(
            String lawId,
            UpdateLawBaseRequest request,
            String operatorId) {
        LawDocument law = mutableLaw(lawId);
        if (request == null) {
            throw validation("baseInfo", "法律基础信息不能为空");
        }
        String name;
        String normalizedName;
        String authority;
        try {
            name = LawDomainRules.validateLawName(request.name());
            normalizedName = LawDomainRules.normalizeLawName(name);
            authority = LawDomainRules.validateIssuingAuthority(request.issuingAuthority());
            LawDomainRules.requirePublicationDate(request.publicationDate());
            LawDomainRules.requireValidityStatus(request.validityStatus());
        } catch (IllegalArgumentException exception) {
            throw validation("baseInfo", exception.getMessage());
        }
        lawRepository.findByNormalizedName(normalizedName)
                .filter(existing -> !existing.getId().equals(lawId))
                .ifPresent(existing -> {
                    throw nameConflict();
                });
        Map<String, Object> before = baseSnapshot(law);
        Map<String, Object> after = baseSnapshot(
                name,
                authority,
                request.publicationDate(),
                request.validityStatus());
        if (before.equals(after)) {
            return lawQueryService.getDetail(lawId);
        }

        Instant now = Instant.now();
        LawAuditDocument audit = LawAuditDocument.create(
                lawId,
                LawAuditType.BASE_INFO,
                before,
                after,
                operatorId,
                now);
        lawAuditRepository.insert(audit);
        try {
            UpdateResult result = mongoTemplate.updateFirst(
                    currentLawQuery(law),
                    new Update()
                            .set("name", name)
                            .set("normalizedName", normalizedName)
                            .set("issuingAuthority", authority)
                            .set("publicationDate", request.publicationDate())
                            .set("validityStatus", request.validityStatus())
                            .set("updatedAt", now),
                    LawDocument.class);
            requireUpdated(result);
        } catch (DuplicateKeyException exception) {
            compensateAudit(audit.getId(), exception);
            throw nameConflict();
        } catch (RuntimeException exception) {
            compensateAudit(audit.getId(), exception);
            throw exception;
        }
        return lawQueryService.getDetail(lawId);
    }

    public LawDetailResponse updateStructure(
            String lawId,
            UpdateLawStructureRequest request,
            String operatorId) {
        LawDocument law = mutableLaw(lawId);
        ContentVersionDocument version = lawQueryService.requireCurrentVersion(law);
        if (request == null || request.structure() == null) {
            throw validation("structure", "结构不能为空");
        }
        Map<String, String> articleIds = new HashMap<>();
        version.getSemanticArticlesSnapshot()
                .forEach(article -> articleIds.put(article.getArticleId(), article.getArticleId()));
        List<LawStructureNode> structure;
        try {
            structure = LawImportService.toStructure(request.structure(), articleIds);
            structure = LawStructureValidator.validate(structure, articleIds.keySet());
        } catch (IllegalArgumentException exception) {
            throw validation("structure", exception.getMessage());
        }
        if (structureSnapshot(law.getStructure()).equals(structureSnapshot(structure))) {
            return lawQueryService.getDetail(lawId);
        }
        Instant now = Instant.now();
        LawAuditDocument audit = LawAuditDocument.create(
                lawId,
                LawAuditType.STRUCTURE,
                Map.of("structure", structureSnapshot(law.getStructure())),
                Map.of("structure", structureSnapshot(structure)),
                operatorId,
                now);
        lawAuditRepository.insert(audit);
        try {
            UpdateResult result = mongoTemplate.updateFirst(
                    currentLawQuery(law),
                    new Update().set("structure", structure).set("updatedAt", now),
                    LawDocument.class);
            requireUpdated(result);
        } catch (RuntimeException exception) {
            compensateAudit(audit.getId(), exception);
            throw exception;
        }
        return lawQueryService.getDetail(lawId);
    }

    public LawDetailResponse addArticle(
            String lawId,
            CreateLawArticleRequest request,
            String operatorId) {
        LawDocument law = mutableLaw(lawId);
        ContentVersionDocument current = lawQueryService.requireCurrentVersion(law);
        if (request == null) {
            throw validation("article", "法条不能为空");
        }
        List<ArticleSnapshot> articles = new ArrayList<>(current.getSemanticArticlesSnapshot());
        ArticleSnapshot added;
        try {
            added = ArticleSnapshot.createNew(request.number(), request.body(), request.order());
            articles.add(added);
            articles = validatedArticles(articles);
        } catch (IllegalArgumentException exception) {
            throw validation("article", exception.getMessage());
        }
        String articleId = added.getArticleId();
        return appendSemanticVersion(
                law,
                current,
                articles,
                operatorId,
                pending -> pending.recordAddition(articleId),
                null,
                null);
    }

    public LawDetailResponse updateArticle(
            String lawId,
            String articleId,
            UpdateLawArticleRequest request,
            String operatorId) {
        LawDocument law = mutableLaw(lawId);
        ContentVersionDocument current = lawQueryService.requireCurrentVersion(law);
        if (request == null) {
            throw validation("article", "法条不能为空");
        }
        boolean found = false;
        List<ArticleSnapshot> articles = new ArrayList<>();
        try {
            for (ArticleSnapshot article : current.getSemanticArticlesSnapshot()) {
                if (article.getArticleId().equals(articleId)) {
                    found = true;
                    articles.add(ArticleSnapshot.carryForward(
                            articleId,
                            request.number(),
                            request.body(),
                            request.order()));
                } else {
                    articles.add(article);
                }
            }
            if (!found) {
                throw articleNotFound();
            }
            articles = validatedArticles(articles);
        } catch (ApiException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw validation("article", exception.getMessage());
        }
        return appendSemanticVersion(
                law,
                current,
                articles,
                operatorId,
                pending -> pending.recordModification(articleId),
                null,
                null);
    }

    public LawDetailResponse deleteArticle(
            String lawId,
            String articleId,
            String operatorId) {
        LawDocument law = mutableLaw(lawId);
        ContentVersionDocument current = lawQueryService.requireCurrentVersion(law);
        if (current.getSemanticArticlesSnapshot().size() == 1
                && current.getSemanticArticlesSnapshot().getFirst().getArticleId().equals(articleId)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    LawErrorCodes.LAST_ARTICLE_REQUIRED,
                    "不能删除最后一条法条");
        }
        List<ArticleSnapshot> articles = current.getSemanticArticlesSnapshot().stream()
                .filter(article -> !article.getArticleId().equals(articleId))
                .toList();
        if (articles.size() == current.getSemanticArticlesSnapshot().size()) {
            throw articleNotFound();
        }
        List<LawStructureNode> updatedStructure = law.getStructure().stream()
                .map(node -> new LawStructureNode(
                        node.getNodeId(),
                        node.getType(),
                        node.getTitle(),
                        node.getParentNodeId(),
                        node.getOrder(),
                        node.getArticleIds().stream()
                                .filter(id -> !id.equals(articleId))
                                .toList()))
                .toList();
        LawAuditDocument structureAudit = null;
        if (!structureSnapshot(law.getStructure()).equals(structureSnapshot(updatedStructure))) {
            Instant now = Instant.now();
            structureAudit = LawAuditDocument.create(
                    lawId,
                    LawAuditType.STRUCTURE,
                    Map.of("structure", structureSnapshot(law.getStructure())),
                    Map.of("structure", structureSnapshot(updatedStructure)),
                    operatorId,
                    now);
        }
        return appendSemanticVersion(
                law,
                current,
                articles,
                operatorId,
                pending -> pending.recordDeletion(articleId),
                updatedStructure,
                structureAudit);
    }

    public void deleteLaw(String lawId) {
        LawDocument law = mutableLaw(lawId);
        Instant now = Instant.now();
        UpdateResult result = mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id")
                        .is(law.getId())
                        .and("deletedAt").is(null)
                        .and("currentContentVersionId").is(law.getCurrentContentVersionId())),
                new Update().set("deletedAt", now).set("updatedAt", now),
                LawDocument.class);
        if (result.getModifiedCount() != 1) {
            throw versionConflict();
        }
    }

    private LawDetailResponse appendSemanticVersion(
            LawDocument law,
            ContentVersionDocument current,
            List<ArticleSnapshot> articles,
            String operatorId,
            UnaryOperator<PendingChangeSet> pendingChange,
            List<LawStructureNode> updatedStructure,
            LawAuditDocument structureAudit) {
        Instant now = Instant.now();
        ContentVersionDocument next;
        try {
            next = ContentVersionDocument.create(
                    law.getId(),
                    current.getSeq() + 1,
                    articles,
                    operatorId,
                    now);
        } catch (IllegalArgumentException exception) {
            throw validation("articles", exception.getMessage());
        }
        try {
            contentVersionRepository.insert(next);
        } catch (DuplicateKeyException exception) {
            throw versionConflict();
        }
        if (structureAudit != null) {
            try {
                lawAuditRepository.insert(structureAudit);
            } catch (RuntimeException exception) {
                compensateContentVersion(next.getId(), exception);
                throw exception;
            }
        }
        boolean pendingRevision = law.getCurrentAnnotationVersionId() != null;
        PendingChangeSet pendingChangeSet = pendingRevision
                ? pendingChange.apply(law.getPendingChangeSet())
                : PendingChangeSet.empty();
        Update update = new Update()
                .set("currentContentVersionId", next.getId())
                .set("pendingRevision", pendingRevision)
                .set("pendingChangeSet", pendingChangeSet)
                .set("updatedAt", now);
        if (updatedStructure != null) {
            update.set("structure", updatedStructure);
        }
        try {
            UpdateResult result = mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id")
                            .is(law.getId())
                            .and("deletedAt").is(null)
                            .and("currentContentVersionId").is(current.getId())),
                    update,
                    LawDocument.class);
            if (result.getModifiedCount() != 1) {
                RuntimeException conflict = versionConflict();
                compensateSemanticArtifacts(next.getId(), structureAudit, conflict);
                throw conflict;
            }
        } catch (RuntimeException exception) {
            if (!(exception instanceof ApiException)) {
                compensateSemanticArtifacts(next.getId(), structureAudit, exception);
            }
            throw exception;
        }
        return lawQueryService.getDetail(law.getId());
    }

    private LawDocument mutableLaw(String lawId) {
        LawDocument law = lawQueryService.requireVisibleLaw(lawId);
        mutationGuards.forEach(guard -> guard.assertMutationAllowed(lawId));
        return law;
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

    private static Query currentLawQuery(LawDocument law) {
        return Query.query(Criteria.where("_id")
                .is(law.getId())
                .and("deletedAt").is(null)
                .and("updatedAt").is(law.getUpdatedAt()));
    }

    private static void requireUpdated(UpdateResult result) {
        if (result.getModifiedCount() != 1) {
            throw versionConflict();
        }
    }

    private void compensateSemanticArtifacts(
            String contentVersionId,
            LawAuditDocument audit,
            RuntimeException originalFailure) {
        if (audit != null) {
            compensateAudit(audit.getId(), originalFailure);
        }
        compensateContentVersion(contentVersionId, originalFailure);
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

    private void compensateAudit(String auditId, RuntimeException originalFailure) {
        try {
            mongoTemplate.remove(
                    Query.query(Criteria.where("_id").is(auditId)),
                    LawAuditDocument.class);
        } catch (RuntimeException compensationFailure) {
            originalFailure.addSuppressed(compensationFailure);
        }
    }

    private static Map<String, Object> baseSnapshot(LawDocument law) {
        return baseSnapshot(
                law.getName(),
                law.getIssuingAuthority(),
                law.getPublicationDate(),
                law.getValidityStatus());
    }

    private static Map<String, Object> baseSnapshot(
            String name,
            String authority,
            Object publicationDate,
            Object validityStatus) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", name);
        snapshot.put("issuingAuthority", authority);
        snapshot.put("publicationDate", publicationDate);
        snapshot.put("validityStatus", validityStatus);
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

    private static ApiException articleNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                LawErrorCodes.NOT_FOUND,
                "法条不存在");
    }

    private static ApiException versionConflict() {
        return new ApiException(
                HttpStatus.CONFLICT,
                LawErrorCodes.VERSION_CONFLICT,
                "法律内容已发生变化，请刷新后重试");
    }
}
