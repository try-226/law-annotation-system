package com.law.annotation.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;

import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.law.dto.CreateLawArticleRequest;
import com.law.annotation.law.dto.LawDetailResponse;
import com.law.annotation.law.dto.UpdateLawArticleRequest;
import com.law.annotation.law.dto.UpdateLawBaseRequest;
import com.law.annotation.law.dto.UpdateLawStructureRequest;
import com.law.annotation.version.ContentVersionDocument;
import com.law.annotation.version.ContentVersionRepository;
import com.law.annotation.task.TaskLawMutationGuard;
import com.law.annotation.task.TaskRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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

class LawVersionRecycleIntegrationTests {

    private static MongoServer mongoServer;
    private static MongoClient mongoClient;
    private static MongoTemplate mongoTemplate;
    private static LawRepository lawRepository;
    private static ContentVersionRepository contentVersionRepository;
    private static LawAuditRepository lawAuditRepository;
    private static LawQueryService queryService;

    @BeforeAll
    static void startMongo() {
        mongoServer = new MongoServer(new MemoryBackend());
        mongoClient = MongoClients.create(mongoServer.bindAndGetConnectionString());
        mongoTemplate = new MongoTemplate(mongoClient, "law_pr09_test");
        MongoRepositoryFactory factory = new MongoRepositoryFactory(mongoTemplate);
        lawRepository = factory.getRepository(LawRepository.class);
        contentVersionRepository = factory.getRepository(ContentVersionRepository.class);
        lawAuditRepository = factory.getRepository(LawAuditRepository.class);
        TaskRepository taskRepository = factory.getRepository(TaskRepository.class);
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
        mongoTemplate.remove(new Query(), "tasks");
    }

    @Test
    void activeTaskReturnsLawLockErrorForEveryMutationPath() {
        InitialLawCreation creation = createLaw("任务锁定测试法");
        String lawId = creation.law().getId();
        String articleId = creation.contentVersion()
                .getSemanticArticlesSnapshot().getFirst().getArticleId();
        TaskRepository taskRepository = mock(TaskRepository.class);
        when(taskRepository.existsByLawIdAndTaskStateIn(
                eq(lawId), anyCollection())).thenReturn(true);
        LawMutationGuard activeTaskGuard = new TaskLawMutationGuard(taskRepository);
        LawMaintenanceService maintenanceService = maintenanceService(List.of(activeTaskGuard));
        LawRecycleService recycleService = recycleService(List.of(activeTaskGuard));

        assertLawLocked(() -> maintenanceService.updateBase(
                lawId,
                new UpdateLawBaseRequest(
                        "被阻止的新名称",
                        "制定机关",
                        LocalDate.of(2026, 8, 19),
                        ValidityStatus.ACTIVE),
                "admin-1"));
        assertLawLocked(() -> maintenanceService.updateStructure(
                lawId,
                new UpdateLawStructureRequest(List.of()),
                "admin-1"));
        assertLawLocked(() -> maintenanceService.updateArticle(
                lawId,
                articleId,
                new UpdateLawArticleRequest("第一条", "被阻止的新正文", 0),
                "admin-1"));
        assertLawLocked(() -> recycleService.deleteLaw(lawId));

        assertThat(contentVersionRepository.findByLawIdOrderBySeqAsc(lawId))
                .hasSize(1);
        assertThat(lawAuditRepository.findByLawIdOrderByOperatedAtDesc(lawId))
                .isEmpty();
    }

    @Test
    void nameOnlyUpdateAppendsAuditWithoutCreatingContentVersion() {
        InitialLawCreation creation = createLaw("修改前名称");
        LawDetailResponse updated = maintenanceService(List.of()).updateBase(
                creation.law().getId(),
                new UpdateLawBaseRequest(
                        "修改后名称",
                        creation.law().getIssuingAuthority(),
                        creation.law().getPublicationDate(),
                        creation.law().getValidityStatus()),
                "admin-1");

        assertThat(updated.name()).isEqualTo("修改后名称");
        assertThat(contentVersionRepository.findByLawIdOrderBySeqAsc(creation.law().getId()))
                .hasSize(1);
        assertThat(lawAuditRepository.findByLawIdOrderByOperatedAtDesc(creation.law().getId()))
                .singleElement()
                .satisfies(audit -> {
                    assertThat(audit.getAuditType()).isEqualTo(LawAuditType.BASE_INFO);
                    assertThat(audit.getBefore()).containsEntry("name", "修改前名称");
                    assertThat(audit.getAfter()).containsEntry("name", "修改后名称");
                    assertThat(audit.getOperatorId()).isEqualTo("admin-1");
                });
    }

    @Test
    void bodyUpdateCreatesNewImmutableContentVersionWithoutFormalAnnotation() {
        InitialLawCreation creation = createLaw("正文版本测试法");
        String articleId = creation.contentVersion()
                .getSemanticArticlesSnapshot().getFirst().getArticleId();
        LawDetailResponse updated = maintenanceService(List.of()).updateArticle(
                creation.law().getId(),
                articleId,
                new UpdateLawArticleRequest("第一条", "第二版正文", 0),
                "admin-1");

        List<ContentVersionDocument> versions = contentVersionRepository
                .findByLawIdOrderBySeqAsc(creation.law().getId());
        assertThat(versions).hasSize(2);
        assertThat(versions.getFirst().getSemanticArticlesSnapshot().getFirst().getBody())
                .isEqualTo("第一版正文");
        assertThat(versions.get(1).getSemanticArticlesSnapshot().getFirst().getBody())
                .isEqualTo("第二版正文");
        assertThat(updated.currentContentVersionSeq()).isEqualTo(2);
        assertThat(updated.pendingRevision()).isFalse();
    }

    @Test
    void formalAnnotationMakesSemanticUpdatePendingRevisionAndRecordsChangeSet() {
        InitialLawCreation creation = createLaw("待修订测试法");
        String lawId = creation.law().getId();
        String articleId = creation.contentVersion().getSemanticArticlesSnapshot().getFirst().getArticleId();
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(lawId)),
                new Update().set("currentAnnotationVersionId", "annotation-1"),
                LawDocument.class);
        LawDetailResponse updated = maintenanceService(List.of()).updateArticle(
                lawId,
                articleId,
                new UpdateLawArticleRequest("第一条", "正式标注后的新正文", 0),
                "admin-1");

        LawDocument stored = lawRepository.findById(lawId).orElseThrow();
        assertThat(updated.pendingRevision()).isTrue();
        assertThat(stored.getPendingChangeSet().getModifiedArticleIds())
                .containsExactly(articleId);
        assertThat(stored.getPendingChangeSet().getAddedArticleIds()).isEmpty();
        assertThat(stored.getPendingChangeSet().getDeletedArticleIds()).isEmpty();
    }

    @Test
    void granularArticleMutationsClassifyAddedModifiedAndDeletedArticleIds() {
        InitialLawCreation creation = createLaw("变更集合分类测试法");
        String lawId = creation.law().getId();
        String firstId = creation.contentVersion()
                .getSemanticArticlesSnapshot().getFirst().getArticleId();
        LawMaintenanceService service = maintenanceService(List.of());
        LawDetailResponse c2 = service.addArticle(
                lawId,
                new CreateLawArticleRequest("第二条", "第二条正文", 1),
                "admin-1");
        String secondId = c2.articles().stream()
                .filter(article -> article.number().equals("第二条"))
                .findFirst()
                .orElseThrow()
                .articleId();
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(lawId)),
                new Update().set("currentAnnotationVersionId", "annotation-1"),
                LawDocument.class);

        LawDetailResponse afterAdd = service.addArticle(
                lawId,
                new CreateLawArticleRequest("第三条", "新增法条正文", 2),
                "admin-1");

        String thirdId = afterAdd.articles().stream()
                .filter(article -> article.number().equals("第三条"))
                .findFirst()
                .orElseThrow()
                .articleId();
        service.updateArticle(
                lawId,
                firstId,
                new UpdateLawArticleRequest("第一条", "修改后的第一条正文", 0),
                "admin-1");
        LawDetailResponse c5 = service.deleteArticle(lawId, secondId, "admin-1");

        LawDocument stored = lawRepository.findById(lawId).orElseThrow();
        assertThat(stored.getPendingChangeSet().getAddedArticleIds()).containsExactly(thirdId);
        assertThat(stored.getPendingChangeSet().getModifiedArticleIds())
                .containsExactly(firstId);
        assertThat(stored.getPendingChangeSet().getDeletedArticleIds()).containsExactly(secondId);
        assertThat(c5.articles()).extracting(LawDetailResponse.Article::articleId)
                .containsExactly(firstId, thirdId)
                .doesNotContain(secondId);
    }

    @Test
    void lawWithVersionHistoryIsSoftDeletedAndListedInRecycleBin() {
        InitialLawCreation creation = createLaw("历史软删除测试法");
        String articleId = creation.contentVersion()
                .getSemanticArticlesSnapshot().getFirst().getArticleId();
        maintenanceService(List.of()).updateArticle(
                creation.law().getId(),
                articleId,
                new UpdateLawArticleRequest("第一条", "形成C2的正文", 0),
                "admin-1");

        recycleService(List.of()).deleteLaw(creation.law().getId());

        LawDocument deleted = lawRepository.findById(creation.law().getId()).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(mongoTemplate.getCollection("laws")
                        .find(new Document("_id", creation.law().getId()))
                        .first())
                .doesNotContainKey("deleted");
        assertThat(contentVersionRepository.findByLawIdOrderBySeqAsc(creation.law().getId()))
                .hasSize(2);
        assertThat(queryService.list(null, 0, 10).items()).isEmpty();
        assertThat(queryService.listRecycle(null, 0, 10).items())
                .singleElement()
                .satisfies(item -> assertThat(item.id()).isEqualTo(creation.law().getId()));
    }

    @Test
    void lawWithOnlyHistoricalTaskAndC1IsSoftDeleted() {
        InitialLawCreation creation = createLaw("仅任务历史测试法");
        mongoTemplate.getCollection("tasks").insertOne(new Document("_id", "task-history")
                .append("lawId", creation.law().getId())
                .append("taskState", "CANCELED"));

        recycleService(List.of()).deleteLaw(creation.law().getId());

        LawDocument stored = lawRepository.findById(creation.law().getId()).orElseThrow();
        assertThat(stored.getDeletedAt()).isNotNull();
        assertThat(contentVersionRepository.findByLawIdOrderBySeqAsc(creation.law().getId()))
                .hasSize(1);
    }

    @Test
    void lawWithOnlyAuditHistoryAndC1IsSoftDeleted() {
        InitialLawCreation creation = createLaw("仅审计历史测试法");
        lawAuditRepository.insert(LawAuditDocument.create(
                creation.law().getId(),
                LawAuditType.BASE_INFO,
                java.util.Map.of("name", "旧名称"),
                java.util.Map.of("name", creation.law().getName()),
                "admin-1",
                Instant.now()));

        recycleService(List.of()).deleteLaw(creation.law().getId());

        LawDocument stored = lawRepository.findById(creation.law().getId()).orElseThrow();
        assertThat(stored.getDeletedAt()).isNotNull();
        assertThat(contentVersionRepository.findByLawIdOrderBySeqAsc(creation.law().getId()))
                .hasSize(1);
    }

    @Test
    void lawWithFormalAnnotationAndC1IsSoftDeleted() {
        InitialLawCreation creation = createLaw("正式标注历史测试法");
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(creation.law().getId())),
                new Update().set("currentAnnotationVersionId", "annotation-1"),
                LawDocument.class);

        recycleService(List.of()).deleteLaw(creation.law().getId());

        LawDocument stored = lawRepository.findById(creation.law().getId()).orElseThrow();
        assertThat(stored.getDeletedAt()).isNotNull();
        assertThat(stored.getCurrentAnnotationVersionId()).isEqualTo("annotation-1");
        assertThat(contentVersionRepository.findByLawIdOrderBySeqAsc(creation.law().getId()))
                .hasSize(1);
    }

    @Test
    void activeTaskAlsoBlocksWholeLawDeletion() {
        InitialLawCreation creation = createLaw("删除锁定测试法");
        TaskRepository taskRepository = mock(TaskRepository.class);
        when(taskRepository.existsByLawIdAndTaskStateIn(
                eq(creation.law().getId()), anyCollection())).thenReturn(true);
        LawRecycleService service = recycleService(List.of(new TaskLawMutationGuard(taskRepository)));

        assertThatThrownBy(() -> service.deleteLaw(creation.law().getId()))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(LawErrorCodes.ACTIVE_TASK_EXISTS);
        assertThat(lawRepository.findById(creation.law().getId())).isPresent();
        assertThat(contentVersionRepository.findByLawIdOrderBySeqAsc(creation.law().getId()))
                .hasSize(1);
    }

    @Test
    void lawWithoutBusinessHistoryIsPhysicallyDeletedWithItsOnlyC1() {
        InitialLawCreation creation = createLaw("无历史物理删除测试法");

        recycleService(List.of()).deleteLaw(creation.law().getId());

        assertThat(lawRepository.findById(creation.law().getId())).isEmpty();
        assertThat(contentVersionRepository.findByLawIdOrderBySeqAsc(creation.law().getId()))
                .isEmpty();
        assertThat(queryService.listRecycle(null, 0, 10).items()).isEmpty();
    }

    @Test
    void softDeletedLawCanBeRestoredIntoNormalList() {
        InitialLawCreation creation = createLaw("恢复成功测试法");
        String articleId = creation.contentVersion()
                .getSemanticArticlesSnapshot().getFirst().getArticleId();
        maintenanceService(List.of()).updateArticle(
                creation.law().getId(),
                articleId,
                new UpdateLawArticleRequest("第一条", "形成历史以便软删除", 0),
                "admin-1");
        PendingChangeSet pendingChanges = PendingChangeSet.empty()
                .recordModification(articleId);
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(creation.law().getId())),
                new Update()
                        .set("currentAnnotationVersionId", "annotation-1")
                        .set("pendingRevision", true)
                        .set("pendingChangeSet", pendingChanges),
                LawDocument.class);
        LawRecycleService recycleService = recycleService(List.of());
        recycleService.deleteLaw(creation.law().getId());

        LawDetailResponse restored = recycleService.restoreLaw(creation.law().getId());

        assertThat(restored.id()).isEqualTo(creation.law().getId());
        assertThat(restored.displayStatus()).isEqualTo(LawDisplayStatus.PENDING_REVISION);
        assertThat(lawRepository.findById(creation.law().getId()).orElseThrow().isDeleted())
                .isFalse();
        LawDocument stored = lawRepository.findById(creation.law().getId()).orElseThrow();
        assertThat(stored.getCurrentAnnotationVersionId()).isEqualTo("annotation-1");
        assertThat(stored.isPendingRevision()).isTrue();
        assertThat(stored.getPendingChangeSet().getModifiedArticleIds())
                .containsExactly(articleId);
        assertThat(queryService.list(null, 0, 10).items())
                .singleElement()
                .satisfies(item -> assertThat(item.id()).isEqualTo(creation.law().getId()));
        assertThat(queryService.listRecycle(null, 0, 10).items()).isEmpty();
    }

    @Test
    void softDeletedLawKeepsNormalizedNameOccupied() {
        InitialLawCreation creation = createLaw("回收站名称占用测试法");
        lawAuditRepository.insert(LawAuditDocument.create(
                creation.law().getId(),
                LawAuditType.BASE_INFO,
                java.util.Map.of(),
                java.util.Map.of("name", creation.law().getName()),
                "admin-1",
                Instant.now()));
        recycleService(List.of()).deleteLaw(creation.law().getId());

        assertThatThrownBy(() -> createLaw("回收站名称占用测试法"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(LawErrorCodes.NAME_ALREADY_EXISTS);
    }

    @Test
    void restoreFailsWhenNormalizedNameIsOwnedByAnotherLaw() {
        LawRepository repository = mock(LawRepository.class);
        LawQueryService mockedQueryService = mock(LawQueryService.class);
        MongoTemplate template = mock(MongoTemplate.class);
        Instant now = Instant.parse("2026-08-19T00:00:00Z");
        LawDocument deleted = LawDocument.createInitial(
                "law-1",
                "冲突名称测试法",
                "制定机关",
                LocalDate.of(2026, 8, 19),
                ValidityStatus.ACTIVE,
                List.of(),
                "content-1",
                now);
        deleted.markDeleted(now.plusSeconds(60));
        LawDocument conflict = LawDocument.createInitial(
                "law-2",
                "冲突名称测试法",
                "制定机关",
                LocalDate.of(2026, 8, 19),
                ValidityStatus.ACTIVE,
                List.of(),
                "content-2",
                now);
        ContentVersionDocument version = new ContentVersionDocument(
                "content-1",
                "law-1",
                1,
                List.of(ArticleSnapshot.createNew("第一条", "正文", 0)),
                "admin-1",
                now);
        when(mockedQueryService.requireDeletedLaw("law-1")).thenReturn(deleted);
        when(mockedQueryService.requireCurrentVersion(deleted)).thenReturn(version);
        when(repository.findFirstByNormalizedNameAndIdNot("冲突名称测试法", "law-1"))
                .thenReturn(Optional.of(conflict));
        LawRecycleService service = new LawRecycleService(
                repository,
                mockedQueryService,
                template,
                List.of(),
                new LawOperationCoordinator(template));

        assertThatThrownBy(() -> service.restoreLaw("law-1"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(LawErrorCodes.NAME_ALREADY_EXISTS);
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
                        List.of(new NewArticleDraft("第一条", "第一版正文", 0)),
                        "admin-1");
    }

    private static void assertLawLocked(Runnable mutation) {
        assertThatThrownBy(mutation::run)
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(LawErrorCodes.ACTIVE_TASK_EXISTS)
                .isNotEqualTo("TASK_ALREADY_EXISTS");
    }
}
