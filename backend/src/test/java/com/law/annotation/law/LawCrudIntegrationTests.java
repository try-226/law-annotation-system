package com.law.annotation.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.common.response.PageResponse;
import com.law.annotation.law.dto.CreateLawArticleRequest;
import com.law.annotation.law.dto.LawBaseInfoInput;
import com.law.annotation.law.dto.LawDetailResponse;
import com.law.annotation.law.dto.LawImportArticleInput;
import com.law.annotation.law.dto.LawImportConfirmRequest;
import com.law.annotation.law.dto.LawListItemResponse;
import com.law.annotation.law.dto.LawStructureInput;
import com.law.annotation.law.dto.UpdateLawArticleRequest;
import com.law.annotation.law.dto.UpdateLawBaseRequest;
import com.law.annotation.law.dto.UpdateLawStructureRequest;
import com.law.annotation.task.TaskRepository;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.version.ContentVersionDocument;
import com.law.annotation.version.ContentVersionRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;

class LawCrudIntegrationTests {

    private static MongoServer mongoServer;
    private static MongoClient mongoClient;
    private static MongoTemplate mongoTemplate;
    private static LawRepository lawRepository;
    private static ContentVersionRepository contentVersionRepository;
    private static LawAuditRepository lawAuditRepository;
    private static TaskRepository taskRepository;
    private static LawQueryService queryService;

    @BeforeAll
    static void startMongo() {
        mongoServer = new MongoServer(new MemoryBackend());
        mongoClient = MongoClients.create(mongoServer.bindAndGetConnectionString());
        mongoTemplate = new MongoTemplate(mongoClient, "law_crud_test");
        MongoRepositoryFactory factory = new MongoRepositoryFactory(mongoTemplate);
        lawRepository = factory.getRepository(LawRepository.class);
        contentVersionRepository = factory.getRepository(ContentVersionRepository.class);
        lawAuditRepository = factory.getRepository(LawAuditRepository.class);
        taskRepository = factory.getRepository(TaskRepository.class);
        new LawDomainIndexInitializer(mongoTemplate).run(new DefaultApplicationArguments());
        queryService = new LawQueryService(
                lawRepository,
                new LawSearchRepository(mongoTemplate),
                contentVersionRepository,
                taskRepository,
                new LawDisplayStatusResolver());
    }

    @AfterAll
    static void stopMongo() {
        mongoClient.close();
        mongoServer.shutdown();
    }

    @BeforeEach
    void clearDocuments() {
        mongoTemplate.remove(new Query(), LawDocument.class);
        mongoTemplate.remove(new Query(), ContentVersionDocument.class);
        mongoTemplate.remove(new Query(), LawAuditDocument.class);
        mongoTemplate.remove(new Query(), TaskDocument.class);
    }

    @Test
    void listExcludesDeletedUsesDefaultSortAndCountsCurrentArticles() {
        insertLaw("law-1", "第一测试法", Instant.parse("2026-08-19T01:00:00Z"), 1, false);
        insertLaw("law-2", "第二测试法", Instant.parse("2026-08-19T02:00:00Z"), 2, false);
        insertLaw("law-3", "已删除测试法", Instant.parse("2026-08-19T03:00:00Z"), 1, true);

        PageResponse<LawListItemResponse> page = queryService.list(null, 0, 10);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.items()).extracting(LawListItemResponse::id)
                .containsExactly("law-2", "law-1");
        assertThat(page.items().getFirst().articleCount()).isEqualTo(2);
        assertThat(queryService.list("第一", 0, 10).items())
                .extracting(LawListItemResponse::id)
                .containsExactly("law-1");
    }

    @Test
    void listUsesRequestedPageAndTenItemDefaultSize() {
        for (int index = 0; index < 12; index++) {
            insertLaw(
                    "paged-law-" + index,
                    "分页测试法" + index,
                    Instant.parse("2026-08-19T00:00:00Z").plusSeconds(index),
                    1,
                    false);
        }

        PageResponse<LawListItemResponse> first = queryService.list(null, 0, 10);
        PageResponse<LawListItemResponse> second = queryService.list(null, 1, 10);

        assertThat(first.items()).hasSize(10);
        assertThat(second.items()).hasSize(2);
        assertThat(first.totalElements()).isEqualTo(12);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.items().getFirst().id()).isEqualTo("paged-law-11");
    }

    @Test
    void listFiltersActiveStatusesByTaskTypeAndTaskState() {
        Instant now = Instant.parse("2026-08-25T00:00:00Z");
        insertLaw("ordinary-law", "组合民法普通", now, 1, false);
        insertLaw("revision-law", "组合民法修订", now.plusSeconds(1), 1, false);
        insertLaw("review-law", "组合民法待审核", now.plusSeconds(2), 1, false);
        insertLaw("rejected-law", "组合民法部分驳回", now.plusSeconds(3), 1, false);
        insertLaw("rereview-law", "组合民法待复审", now.plusSeconds(4), 1, false);
        insertTask("ordinary-law", TaskType.ORDINARY, TaskState.PENDING_ANNOTATION);
        insertTask("revision-law", TaskType.REVISION, TaskState.PENDING_ANNOTATION);
        insertTask("review-law", TaskType.REVISION, TaskState.PENDING_REVIEW);
        insertTask("rejected-law", TaskType.ORDINARY, TaskState.PARTIALLY_REJECTED);
        insertTask("rereview-law", TaskType.REVISION, TaskState.PENDING_REREVIEW);

        PageResponse<LawListItemResponse> annotating = queryService.list(
                "组合民法", ValidityStatus.ACTIVE, LawDisplayStatus.ANNOTATING, 0, 10);
        PageResponse<LawListItemResponse> revising = queryService.list(
                "组合民法", ValidityStatus.ACTIVE, LawDisplayStatus.REVISING, 0, 10);
        PageResponse<LawListItemResponse> pendingReview = queryService.list(
                "组合民法", ValidityStatus.ACTIVE, LawDisplayStatus.PENDING_REVIEW, 0, 10);
        PageResponse<LawListItemResponse> partiallyRejected = queryService.list(
                "组合民法", ValidityStatus.ACTIVE, LawDisplayStatus.PARTIALLY_REJECTED, 0, 10);
        PageResponse<LawListItemResponse> pendingRereview = queryService.list(
                "组合民法", ValidityStatus.ACTIVE, LawDisplayStatus.PENDING_REREVIEW, 0, 10);

        assertThat(annotating.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo("ordinary-law");
            assertThat(item.displayStatus()).isEqualTo(LawDisplayStatus.ANNOTATING);
        });
        assertThat(revising.items())
                .extracting(LawListItemResponse::id)
                .containsExactly("rereview-law", "review-law", "revision-law");
        assertThat(revising.items())
                .extracting(LawListItemResponse::displayStatus)
                .containsOnly(LawDisplayStatus.REVISING);
        assertThat(pendingReview.items()).isEmpty();
        assertThat(partiallyRejected.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo("rejected-law");
            assertThat(item.displayStatus()).isEqualTo(LawDisplayStatus.PARTIALLY_REJECTED);
        });
        assertThat(pendingRereview.items()).isEmpty();
    }

    @Test
    void listFiltersPassiveStatusesFromLawStateAndExcludesDeletedLaws() {
        Instant now = Instant.parse("2026-08-25T00:00:00Z");
        insertLaw("unannotated-law", "未标注测试法", now, 1, false);
        insertLaw("completed-law", "已完成测试法", now.plusSeconds(1), 1, false);
        insertLaw("pending-law", "待修订测试法", now.plusSeconds(2), 1, false);
        insertLaw("deleted-law", "删除的未标注法", now.plusSeconds(3), 1, true);
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is("completed-law")),
                new Update().set("currentAnnotationVersionId", "annotation-completed"),
                LawDocument.class);
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is("pending-law")),
                new Update()
                        .set("currentAnnotationVersionId", "annotation-pending")
                        .set("pendingRevision", true),
                LawDocument.class);

        assertThat(queryService.list(
                        null, null, LawDisplayStatus.UNANNOTATED, 0, 10).items())
                .extracting(LawListItemResponse::id)
                .containsExactly("unannotated-law");
        assertThat(queryService.list(
                        null, null, LawDisplayStatus.COMPLETED, 0, 10).items())
                .extracting(LawListItemResponse::id)
                .containsExactly("completed-law");
        assertThat(queryService.list(
                        null, null, LawDisplayStatus.PENDING_REVISION, 0, 10).items())
                .extracting(LawListItemResponse::id)
                .containsExactly("pending-law");
    }

    @Test
    void displayStatusFilteringHappensBeforePagingAndCountsFilteredRows() {
        Instant now = Instant.parse("2026-08-25T00:00:00Z");
        for (int index = 0; index < 12; index++) {
            String lawId = "review-law-" + index;
            insertLaw(lawId, "待审核分页法" + index, now.plusSeconds(index), 1, false);
            insertTask(lawId, TaskType.ORDINARY, TaskState.PENDING_REVIEW);
        }
        insertLaw("unrelated-law", "不相关法律", now.plusSeconds(20), 1, false);

        PageResponse<LawListItemResponse> first = queryService.list(
                "待审核分页法", ValidityStatus.ACTIVE, LawDisplayStatus.PENDING_REVIEW, 0, 5);
        PageResponse<LawListItemResponse> third = queryService.list(
                "待审核分页法", ValidityStatus.ACTIVE, LawDisplayStatus.PENDING_REVIEW, 2, 5);

        assertThat(first.items()).hasSize(5);
        assertThat(third.items()).hasSize(2);
        assertThat(first.totalElements()).isEqualTo(12);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.items().getFirst().id()).isEqualTo("review-law-11");
    }

    @Test
    void activeStatusWithNoMatchingTaskReturnsAnEmptyPage() {
        insertLaw("law-1", "无任务测试法", Instant.parse("2026-08-25T00:00:00Z"), 1, false);

        PageResponse<LawListItemResponse> result = queryService.list(
                null, null, LawDisplayStatus.PENDING_REVIEW, 0, 10);

        assertThat(result.items()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    void detailReadsArticlesFromCurrentContentVersionAndMissingIsNotFound() {
        insertLaw("law-1", "测试法", Instant.parse("2026-08-19T01:00:00Z"), 2, false);

        LawDetailResponse detail = queryService.getDetail("law-1");

        assertThat(detail.currentContentVersionId()).isEqualTo("content-law-1");
        assertThat(detail.currentContentVersionSeq()).isEqualTo(1);
        assertThat(detail.currentAnnotationVersionId()).isNull();
        assertThat(detail.articles()).hasSize(2);
        assertThat(detail.displayStatus()).isEqualTo(LawDisplayStatus.UNANNOTATED);

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is("law-1")),
                new Update().set("currentAnnotationVersionId", "annotation-1"),
                LawDocument.class);
        assertThat(queryService.getDetail("law-1").currentAnnotationVersionId())
                .isEqualTo("annotation-1");

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is("law-1")),
                new Update().set("currentAnnotationVersionId", "annotation-2"),
                LawDocument.class);
        assertThat(queryService.getDetail("law-1").currentAnnotationVersionId())
                .isEqualTo("annotation-2");

        assertThatThrownBy(() -> queryService.getDetail("missing"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(LawErrorCodes.NOT_FOUND);
    }

    @Test
    void baseUpdateNormalizesNameAppendsAuditAndDoesNotCreateContentVersion() {
        InitialLawCreation creation = createLaw("旧名称测试法");
        LawMaintenanceService service = maintenanceService(List.of());

        LawDetailResponse updated = service.updateBase(
                creation.law().getId(),
                new UpdateLawBaseRequest(
                        "  新名称测试法  ",
                        "  新制定机关  ",
                        LocalDate.of(2026, 8, 20),
                        ValidityStatus.NOT_EFFECTIVE),
                "admin-1");

        LawDocument stored = lawRepository.findById(creation.law().getId()).orElseThrow();
        assertThat(updated.name()).isEqualTo("新名称测试法");
        assertThat(stored.getNormalizedName()).isEqualTo("新名称测试法");
        assertThat(stored.getCurrentContentVersionId()).isEqualTo(creation.contentVersion().getId());
        assertThat(stored.isPendingRevision()).isFalse();
        assertThat(contentVersionRepository.findByLawIdOrderBySeqAsc(stored.getId())).hasSize(1);
        assertThat(lawAuditRepository.findByLawIdOrderByOperatedAtDesc(stored.getId()))
                .singleElement()
                .extracting(LawAuditDocument::getAuditType)
                .isEqualTo(LawAuditType.BASE_INFO);
    }

    @Test
    void repeatedBaseUpdatesAppendAuditsInsteadOfOverwritingHistory() {
        InitialLawCreation creation = createLaw("审计追加测试法");
        LawMaintenanceService service = maintenanceService(List.of());

        service.updateBase(
                creation.law().getId(),
                new UpdateLawBaseRequest(
                        "审计追加测试法一",
                        "制定机关",
                        LocalDate.of(2026, 8, 19),
                        ValidityStatus.ACTIVE),
                "admin-1");
        service.updateBase(
                creation.law().getId(),
                new UpdateLawBaseRequest(
                        "审计追加测试法二",
                        "制定机关",
                        LocalDate.of(2026, 8, 19),
                        ValidityStatus.ACTIVE),
                "admin-1");

        assertThat(lawAuditRepository.findByLawIdOrderByOperatedAtDesc(creation.law().getId()))
                .hasSize(2)
                .allMatch(audit -> audit.getAuditType() == LawAuditType.BASE_INFO);
        assertThat(contentVersionRepository.findByLawIdOrderBySeqAsc(creation.law().getId()))
                .hasSize(1);
    }

    @Test
    void confirmImportPersistsLawAndImmutableC1ThroughCreationService() {
        LawCreationService creationService = new LawCreationService(
                lawRepository,
                contentVersionRepository,
                mongoTemplate);
        LawImportService importService = new LawImportService(
                new LawTextParser(), creationService, queryService);
        LawImportConfirmRequest request = new LawImportConfirmRequest(
                new LawBaseInfoInput(
                        "导入集成测试法",
                        "制定机关",
                        LocalDate.of(2026, 8, 19),
                        ValidityStatus.ACTIVE),
                List.of(new LawStructureInput(
                        "chapter-1",
                        LawStructureNodeType.CHAPTER,
                        "第一章 总则",
                        null,
                        0,
                        List.of("preview-article-1"))),
                List.of(new LawImportArticleInput(
                        "preview-article-1",
                        "第一条",
                        "导入正文",
                        0)));

        LawDetailResponse detail = importService.confirm(request, "admin-1");

        LawDocument storedLaw = lawRepository.findById(detail.id()).orElseThrow();
        ContentVersionDocument c1 = contentVersionRepository
                .findById(storedLaw.getCurrentContentVersionId())
                .orElseThrow();
        assertThat(c1.getSeq()).isEqualTo(1);
        assertThat(c1.getSemanticArticlesSnapshot()).singleElement()
                .extracting(ArticleSnapshot::getBody)
                .isEqualTo("导入正文");
        assertThat(detail.displayStatus()).isEqualTo(LawDisplayStatus.UNANNOTATED);
        assertThat(storedLaw.getStructure()).singleElement()
                .satisfies(node -> assertThat(node.getArticleIds())
                        .containsExactly(c1.getSemanticArticlesSnapshot().getFirst().getArticleId()));
    }

    @Test
    void duplicateNameUpdateIsRejectedWithoutAudit() {
        InitialLawCreation first = createLaw("第一测试法");
        createLaw("第二测试法");
        LawMaintenanceService service = maintenanceService(List.of());

        assertThatThrownBy(() -> service.updateBase(
                        first.law().getId(),
                        new UpdateLawBaseRequest(
                                " 第二测试法 ",
                                "制定机关",
                                LocalDate.of(2026, 8, 19),
                                ValidityStatus.ACTIVE),
                        "admin-1"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(LawErrorCodes.NAME_ALREADY_EXISTS);
        assertThat(lawAuditRepository.findByLawIdOrderByOperatedAtDesc(first.law().getId())).isEmpty();
    }

    @Test
    void structureUpdateAppendsAuditWithoutCreatingContentVersion() {
        InitialLawCreation creation = createLaw("结构测试法");
        String articleId = creation.contentVersion().getSemanticArticlesSnapshot().getFirst().getArticleId();
        LawMaintenanceService service = maintenanceService(List.of());

        LawDetailResponse updated = service.updateStructure(
                creation.law().getId(),
                new UpdateLawStructureRequest(List.of(new LawStructureInput(
                        "chapter-1",
                        LawStructureNodeType.CHAPTER,
                        "  第一章 总则  ",
                        null,
                        0,
                        List.of(articleId)))),
                "admin-1");

        assertThat(updated.structure()).singleElement()
                .extracting(LawDetailResponse.StructureNode::title)
                .isEqualTo("第一章 总则");
        assertThat(contentVersionRepository.findByLawIdOrderBySeqAsc(creation.law().getId()))
                .hasSize(1);
        assertThat(lawAuditRepository.findByLawIdOrderByOperatedAtDesc(creation.law().getId()))
                .singleElement()
                .extracting(LawAuditDocument::getAuditType)
                .isEqualTo(LawAuditType.STRUCTURE);
    }

    @Test
    void structureUpdateRejectsInvalidTitleWithoutAuditOrContentVersion() {
        InitialLawCreation creation = createLaw("结构非法标题测试法");
        LawMaintenanceService service = maintenanceService(List.of());

        assertThatThrownBy(() -> service.updateStructure(
                        creation.law().getId(),
                        new UpdateLawStructureRequest(List.of(new LawStructureInput(
                                "chapter-1",
                                LawStructureNodeType.CHAPTER,
                                "章".repeat(101),
                                null,
                                0,
                                List.of()))),
                        "admin-1"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(LawErrorCodes.VALIDATION_FAILED);
        assertThat(lawAuditRepository.findByLawIdOrderByOperatedAtDesc(creation.law().getId()))
                .isEmpty();
        assertThat(contentVersionRepository.findByLawIdOrderBySeqAsc(creation.law().getId()))
                .hasSize(1);
    }

    @Test
    void normalizedNoOpArticleUpdateDoesNotCreateVersionOrPendingChanges() {
        InitialLawCreation creation = createLaw("法条无变化测试法");
        String lawId = creation.law().getId();
        String articleId = creation.contentVersion().getSemanticArticlesSnapshot().getFirst().getArticleId();
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(lawId)),
                new Update().set("currentAnnotationVersionId", "annotation-1"),
                LawDocument.class);
        LawMaintenanceService service = maintenanceService(List.of());

        LawDetailResponse updated = service.updateArticle(
                lawId,
                articleId,
                new UpdateLawArticleRequest("第一条", "\n旧正文\n", 0),
                "admin-1");

        LawDocument stored = lawRepository.findById(lawId).orElseThrow();
        assertThat(contentVersionRepository.findByLawIdOrderBySeqAsc(lawId)).hasSize(1);
        assertThat(updated.currentContentVersionId()).isEqualTo(creation.contentVersion().getId());
        assertThat(updated.currentContentVersionSeq()).isEqualTo(1);
        assertThat(stored.getCurrentContentVersionId()).isEqualTo(creation.contentVersion().getId());
        assertThat(updated.pendingRevision()).isFalse();
        assertThat(stored.isPendingRevision()).isFalse();
        assertThat(stored.getPendingChangeSet().isEmpty()).isTrue();
    }

    @Test
    void articleUpdateCreatesC2KeepsStableIdentityAndLeavesC1Immutable() {
        InitialLawCreation creation = createLaw("法条修改测试法");
        String articleId = creation.contentVersion().getSemanticArticlesSnapshot().getFirst().getArticleId();
        LawMaintenanceService service = maintenanceService(List.of());

        LawDetailResponse updated = service.updateArticle(
                creation.law().getId(),
                articleId,
                new UpdateLawArticleRequest("第一条", "新正文", 0),
                "admin-1");

        List<ContentVersionDocument> versions = contentVersionRepository
                .findByLawIdOrderBySeqAsc(creation.law().getId());
        assertThat(versions).hasSize(2);
        assertThat(versions.getFirst().getSemanticArticlesSnapshot().getFirst().getBody())
                .isEqualTo("旧正文");
        assertThat(versions.get(1).getSemanticArticlesSnapshot().getFirst().getArticleId())
                .isEqualTo(articleId);
        assertThat(versions.get(1).getSemanticArticlesSnapshot().getFirst().getBody())
                .isEqualTo("新正文");
        assertThat(updated.currentContentVersionId()).isEqualTo(versions.get(1).getId());
        assertThat(updated.currentContentVersionId()).isNotEqualTo(versions.getFirst().getId());
        assertThat(updated.currentContentVersionSeq()).isEqualTo(2);
        assertThat(updated.pendingRevision()).isFalse();
    }

    @Test
    void articleAddAndDeleteCreateNewVersionsAndDeleteCannotRemoveLastArticle() {
        InitialLawCreation creation = createLaw("法条增删测试法");
        LawMaintenanceService service = maintenanceService(List.of());

        LawDetailResponse afterAdd = service.addArticle(
                creation.law().getId(),
                new CreateLawArticleRequest("第二条", "第二条正文", 1),
                "admin-1");
        String addedId = afterAdd.articles().stream()
                .filter(article -> article.number().equals("第二条"))
                .findFirst()
                .orElseThrow()
                .articleId();
        LawDetailResponse afterDelete = service.deleteArticle(
                creation.law().getId(), addedId, "admin-1");

        List<ContentVersionDocument> versions = contentVersionRepository
                .findByLawIdOrderBySeqAsc(creation.law().getId());
        assertThat(versions).hasSize(3);
        assertThat(versions.get(1).getSemanticArticlesSnapshot())
                .extracting(ArticleSnapshot::getArticleId)
                .contains(addedId);
        assertThat(versions.get(2).getSemanticArticlesSnapshot())
                .extracting(ArticleSnapshot::getArticleId)
                .doesNotContain(addedId);
        assertThat(afterDelete.articles()).hasSize(1);

        String lastId = afterDelete.articles().getFirst().articleId();
        assertThatThrownBy(() -> service.deleteArticle(
                        creation.law().getId(), lastId, "admin-1"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(LawErrorCodes.LAST_ARTICLE_REQUIRED);
        assertThat(contentVersionRepository.findByLawIdOrderBySeqAsc(creation.law().getId()))
                .hasSize(3);
    }

    @Test
    void staleDeleteArticleCannotOverwriteConcurrentStructureUpdate() {
        InitialLawCreation creation = createLaw("语义并发保护测试法");
        String lawId = creation.law().getId();
        LawMaintenanceService service = maintenanceService(List.of());
        LawDetailResponse afterAdd = service.addArticle(
                lawId,
                new CreateLawArticleRequest("第二条", "第二条正文", 1),
                "admin-1");
        String firstId = afterAdd.articles().getFirst().articleId();
        String secondId = afterAdd.articles().get(1).articleId();
        service.updateStructure(
                lawId,
                new UpdateLawStructureRequest(List.of(new LawStructureInput(
                        "chapter-1",
                        LawStructureNodeType.CHAPTER,
                        "第一章 原结构",
                        null,
                        0,
                        List.of(firstId, secondId)))),
                "admin-1");

        LawDocument staleLaw = lawRepository.findById(lawId).orElseThrow();
        List<LawStructureNode> concurrentStructure = List.of(new LawStructureNode(
                "chapter-1",
                LawStructureNodeType.CHAPTER,
                "第一章 并发更新后的结构",
                null,
                0,
                List.of(firstId, secondId)));
        ContentVersionRepository insertingRepository = mock(ContentVersionRepository.class);
        doAnswer(invocation -> {
            ContentVersionDocument next = invocation.getArgument(0);
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(lawId)),
                    new Update()
                            .set("structure", concurrentStructure)
                            .set("updatedAt", staleLaw.getUpdatedAt().plusSeconds(1)),
                    LawDocument.class);
            return contentVersionRepository.insert(next);
        }).when(insertingRepository).insert(any(ContentVersionDocument.class));
        LawMaintenanceService staleService = new LawMaintenanceService(
                lawRepository,
                insertingRepository,
                lawAuditRepository,
                queryService,
                mongoTemplate,
                List.of(),
                new LawOperationCoordinator(mongoTemplate));

        assertThatThrownBy(() -> staleService.deleteArticle(
                        lawId,
                        secondId,
                        "admin-1"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(LawErrorCodes.VERSION_CONFLICT);

        LawDocument stored = lawRepository.findById(lawId).orElseThrow();
        assertThat(stored.getStructure()).singleElement()
                .extracting(LawStructureNode::getTitle)
                .isEqualTo("第一章 并发更新后的结构");
        assertThat(contentVersionRepository.findByLawIdOrderBySeqAsc(lawId)).hasSize(2);
        assertThat(lawAuditRepository.findByLawIdOrderByOperatedAtDesc(lawId)).hasSize(1);
    }

    @Test
    void operationTokenIsCreatedAndReleasedAfterSuccess() {
        InitialLawCreation creation = createLaw("操作令牌成功释放测试法");
        String lawId = creation.law().getId();
        LawOperationCoordinator coordinator = new LawOperationCoordinator(mongoTemplate);

        coordinator.withVisibleLaw(
                lawId,
                () -> new IllegalStateException("法律操作冲突"),
                token -> {
                    Document claimed = rawLaw(lawId);
                    assertThat(claimed.getString(LawOperationCoordinator.OPERATION_TOKEN_FIELD))
                            .isEqualTo(token);
                    assertThat(claimed.getDate(LawOperationCoordinator.OPERATION_EXPIRES_AT_FIELD))
                            .isAfter(new Date());
                    return null;
                });

        assertThat(rawLaw(lawId)).doesNotContainKeys(
                LawOperationCoordinator.OPERATION_TOKEN_FIELD,
                LawOperationCoordinator.OPERATION_EXPIRES_AT_FIELD);
    }

    @Test
    void operationTokenIsReleasedAfterBusinessException() {
        InitialLawCreation creation = createLaw("操作令牌异常释放测试法");
        String lawId = creation.law().getId();
        LawOperationCoordinator coordinator = new LawOperationCoordinator(mongoTemplate);

        assertThatThrownBy(() -> coordinator.withVisibleLaw(
                        lawId,
                        () -> new IllegalStateException("法律操作冲突"),
                        token -> {
                            assertThat(rawLaw(lawId).getString(
                                    LawOperationCoordinator.OPERATION_TOKEN_FIELD))
                                    .isEqualTo(token);
                            throw new ApiException(
                                    org.springframework.http.HttpStatus.BAD_REQUEST,
                                    LawErrorCodes.VALIDATION_FAILED,
                                    "模拟业务异常");
                        }))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(LawErrorCodes.VALIDATION_FAILED);
        assertThat(rawLaw(lawId)).doesNotContainKeys(
                LawOperationCoordinator.OPERATION_TOKEN_FIELD,
                LawOperationCoordinator.OPERATION_EXPIRES_AT_FIELD);
    }

    @Test
    void expiredOperationTokenCanBeRecoveredByANewMutation() {
        InitialLawCreation creation = createLaw("过期操作令牌恢复测试法");
        String lawId = creation.law().getId();
        mongoTemplate.getCollection("laws").updateOne(
                new Document("_id", lawId),
                new Document("$set", new Document(
                        LawOperationCoordinator.OPERATION_TOKEN_FIELD,
                        "stale-token").append(
                        LawOperationCoordinator.OPERATION_EXPIRES_AT_FIELD,
                        Date.from(Instant.now().minusSeconds(1)))));
        LawOperationCoordinator coordinator = new LawOperationCoordinator(mongoTemplate);

        coordinator.withVisibleLaw(
                lawId,
                () -> new IllegalStateException("法律操作冲突"),
                token -> {
                    assertThat(token).isNotEqualTo("stale-token");
                    assertThat(rawLaw(lawId).getString(
                            LawOperationCoordinator.OPERATION_TOKEN_FIELD))
                            .isEqualTo(token);
                    return null;
                });

        assertThat(rawLaw(lawId)).doesNotContainKeys(
                LawOperationCoordinator.OPERATION_TOKEN_FIELD,
                LawOperationCoordinator.OPERATION_EXPIRES_AT_FIELD);
    }

    @Test
    void duplicateNumberMutationIsRejectedWithoutNewVersion() {
        InitialLawCreation creation = createLaw("重复条号测试法");
        LawMaintenanceService service = maintenanceService(List.of());

        assertThatThrownBy(() -> service.addArticle(
                        creation.law().getId(),
                        new CreateLawArticleRequest("第一条", "另一正文", 1),
                        "admin-1"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(LawErrorCodes.VALIDATION_FAILED);
        assertThat(contentVersionRepository.findByLawIdOrderBySeqAsc(creation.law().getId()))
                .hasSize(1);
    }

    @Test
    void deleteWithVersionHistorySoftDeletesAndPreservesVersionsAndNameReservation() {
        InitialLawCreation creation = createLaw("删除测试法");
        LawMaintenanceService maintenanceService = maintenanceService(List.of());
        maintenanceService.updateArticle(
                creation.law().getId(),
                creation.contentVersion().getSemanticArticlesSnapshot().getFirst().getArticleId(),
                new UpdateLawArticleRequest("第一条", "形成历史的新正文", 0),
                "admin-1");
        LawRecycleService recycleService = recycleService(List.of());

        recycleService.deleteLaw(creation.law().getId());

        LawDocument deleted = lawRepository.findById(creation.law().getId()).orElseThrow();
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(lawRepository.existsByNormalizedName("删除测试法")).isTrue();
        assertThat(contentVersionRepository.findByLawIdOrderBySeqAsc(creation.law().getId()))
                .hasSize(2);
        assertThat(queryService.list(null, 0, 10).items()).isEmpty();
    }

    @Test
    void mutationGuardRunsBeforeAnyChange() {
        InitialLawCreation creation = createLaw("任务锁测试法");
        LawMutationGuard guard = lawId -> {
            throw new ApiException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    LawErrorCodes.ACTIVE_TASK_EXISTS,
                    "存在未结束任务");
        };
        LawMaintenanceService service = maintenanceService(List.of(guard));

        assertThatThrownBy(() -> service.updateArticle(
                        creation.law().getId(),
                        creation.contentVersion().getSemanticArticlesSnapshot().getFirst().getArticleId(),
                        new UpdateLawArticleRequest("第一条", "新正文", 0),
                        "admin-1"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(LawErrorCodes.ACTIVE_TASK_EXISTS);
        assertThat(contentVersionRepository.findByLawIdOrderBySeqAsc(creation.law().getId()))
                .hasSize(1);
    }

    @Test
    void semanticChangesAccumulatePendingIdsWhenFormalAnnotationExists() {
        InitialLawCreation creation = createLaw("待修订累计测试法");
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(creation.law().getId())),
                new Update().set("currentAnnotationVersionId", "annotation-1"),
                LawDocument.class);
        LawMaintenanceService service = maintenanceService(List.of());

        LawDetailResponse afterAdd = service.addArticle(
                creation.law().getId(),
                new CreateLawArticleRequest("第二条", "新增正文", 1),
                "admin-1");
        String addedId = afterAdd.articles().stream()
                .filter(article -> article.number().equals("第二条"))
                .findFirst()
                .orElseThrow()
                .articleId();
        String originalId = creation.contentVersion()
                .getSemanticArticlesSnapshot().getFirst().getArticleId();
        LawDetailResponse afterModify = service.updateArticle(
                creation.law().getId(),
                originalId,
                new UpdateLawArticleRequest("第一条", "修改正文", 0),
                "admin-1");

        LawDocument stored = lawRepository.findById(creation.law().getId()).orElseThrow();
        assertThat(afterModify.pendingRevision()).isTrue();
        assertThat(afterModify.displayStatus()).isEqualTo(LawDisplayStatus.PENDING_REVISION);
        assertThat(stored.getPendingChangeSet().getAddedArticleIds()).containsExactly(addedId);
        assertThat(stored.getPendingChangeSet().getModifiedArticleIds()).containsExactly(originalId);
        assertThat(stored.getPendingChangeSet().getDeletedArticleIds()).isEmpty();
    }

    private static LawMaintenanceService maintenanceService(List<LawMutationGuard> guards) {
        return new LawMaintenanceService(
                lawRepository,
                contentVersionRepository,
                lawAuditRepository,
                queryService,
                mongoTemplate,
                guards,
                new LawOperationCoordinator(mongoTemplate));
    }

    private static LawRecycleService recycleService(List<LawMutationGuard> guards) {
        return new LawRecycleService(
                lawRepository,
                queryService,
                mongoTemplate,
                guards,
                new LawOperationCoordinator(mongoTemplate));
    }

    private static InitialLawCreation createLaw(String name) {
        return new LawCreationService(lawRepository, contentVersionRepository, mongoTemplate)
                .createInitialLaw(
                        name,
                        "制定机关",
                        LocalDate.of(2026, 8, 19),
                        ValidityStatus.ACTIVE,
                        List.of(),
                        List.of(new NewArticleDraft("第一条", "旧正文", 0)),
                        "admin-1");
    }

    private static Document rawLaw(String lawId) {
        return mongoTemplate.getCollection("laws")
                .find(new Document("_id", lawId))
                .first();
    }

    private static void insertLaw(
            String id,
            String name,
            Instant updatedAt,
            int articleCount,
            boolean deleted) {
        List<ArticleSnapshot> articles = java.util.stream.IntStream.range(0, articleCount)
                .mapToObj(index -> new ArticleSnapshot(
                        id + "-article-" + index,
                        "第" + (index + 1) + "条",
                        "正文" + index,
                        index))
                .toList();
        ContentVersionDocument version = new ContentVersionDocument(
                "content-" + id,
                id,
                1,
                articles,
                "admin-1",
                updatedAt);
        contentVersionRepository.insert(version);
        LawDocument law = new LawDocument(
                id,
                name,
                LawDomainRules.normalizeLawName(name),
                "制定机关",
                LocalDate.of(2026, 8, 19),
                ValidityStatus.ACTIVE,
                List.of(),
                deleted ? updatedAt.plusSeconds(1) : null,
                version.getId(),
                null,
                false,
                PendingChangeSet.empty(),
                updatedAt.minusSeconds(60),
                updatedAt);
        lawRepository.insert(law);
    }

    private static void insertTask(String lawId, TaskType type, TaskState state) {
        Instant now = Instant.parse("2026-08-25T00:00:00Z");
        taskRepository.insert(new TaskDocument(
                "task-" + lawId,
                type,
                state,
                lawId,
                "annotator-1",
                "测试标注员",
                "测试任务",
                null,
                "content-" + lawId,
                null,
                null,
                List.of(),
                null,
                "admin-1",
                null,
                null,
                null,
                null,
                now,
                now));
    }
}
