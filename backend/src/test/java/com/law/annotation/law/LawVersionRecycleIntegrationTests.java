package com.law.annotation.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;

import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.law.dto.LawBaseInfoInput;
import com.law.annotation.law.dto.LawDetailResponse;
import com.law.annotation.law.dto.UpdateLawArticleInput;
import com.law.annotation.law.dto.UpdateLawRequest;
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
        new LawDomainIndexInitializer(mongoTemplate).run(new DefaultApplicationArguments());
        queryService = new LawQueryService(lawRepository, contentVersionRepository);
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
    void activeTaskBlocksWholeLawUpdateBeforeAuditOrVersionCreation() {
        InitialLawCreation creation = createLaw("任务锁定测试法");
        LawDetailResponse detail = queryService.getDetail(creation.law().getId());
        TaskRepository taskRepository = mock(TaskRepository.class);
        when(taskRepository.existsByLawIdAndTaskStateIn(
                eq(creation.law().getId()), anyCollection())).thenReturn(true);
        LawMutationGuard activeTaskGuard = new TaskLawMutationGuard(taskRepository);

        assertThatThrownBy(() -> updateService(List.of(activeTaskGuard)).updateLaw(
                        creation.law().getId(),
                        updateRequest(detail, detail.name(), "被阻止的新正文"),
                        "admin-1"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("LAW.ACTIVE_TASK_EXISTS");
        assertThat(contentVersionRepository.findByLawIdOrderBySeqAsc(creation.law().getId()))
                .hasSize(1);
        assertThat(lawAuditRepository.findByLawIdOrderByOperatedAtDesc(creation.law().getId()))
                .isEmpty();
    }

    @Test
    void nameOnlyUpdateAppendsAuditWithoutCreatingContentVersion() {
        InitialLawCreation creation = createLaw("修改前名称");
        LawDetailResponse detail = queryService.getDetail(creation.law().getId());

        LawDetailResponse updated = updateService(List.of()).updateLaw(
                creation.law().getId(),
                updateRequest(detail, "修改后名称", detail.articles().getFirst().body()),
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
        LawDetailResponse detail = queryService.getDetail(creation.law().getId());

        LawDetailResponse updated = updateService(List.of()).updateLaw(
                creation.law().getId(),
                updateRequest(detail, detail.name(), "第二版正文"),
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
        LawDetailResponse detail = queryService.getDetail(lawId);

        LawDetailResponse updated = updateService(List.of()).updateLaw(
                lawId,
                updateRequest(detail, detail.name(), "正式标注后的新正文"),
                "admin-1");

        LawDocument stored = lawRepository.findById(lawId).orElseThrow();
        assertThat(updated.pendingRevision()).isTrue();
        assertThat(stored.getPendingChangeSet().getModifiedArticleIds())
                .containsExactly(articleId);
        assertThat(stored.getPendingChangeSet().getAddedArticleIds()).isEmpty();
        assertThat(stored.getPendingChangeSet().getDeletedArticleIds()).isEmpty();
    }

    @Test
    void wholeLawUpdateClassifiesAddedModifiedAndDeletedArticleIds() {
        InitialLawCreation creation = createLaw("变更集合分类测试法");
        String lawId = creation.law().getId();
        LawDetailResponse c1 = queryService.getDetail(lawId);
        LawDetailResponse.Article first = c1.articles().getFirst();
        LawDetailResponse c2 = updateService(List.of()).updateLaw(
                lawId,
                new UpdateLawRequest(
                        new LawBaseInfoInput(
                                c1.name(),
                                c1.issuingAuthority(),
                                c1.publicationDate(),
                                c1.validityStatus()),
                        List.of(),
                        List.of(
                                new UpdateLawArticleInput(
                                        first.articleId(),
                                        first.articleId(),
                                        first.number(),
                                        first.body(),
                                        0),
                                new UpdateLawArticleInput(
                                        null,
                                        "new-second",
                                        "第二条",
                                        "第二条正文",
                                        1))),
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

        LawDetailResponse c3 = updateService(List.of()).updateLaw(
                lawId,
                new UpdateLawRequest(
                        new LawBaseInfoInput(
                                c2.name(),
                                c2.issuingAuthority(),
                                c2.publicationDate(),
                                c2.validityStatus()),
                        List.of(),
                        List.of(
                                new UpdateLawArticleInput(
                                        null,
                                        "new-third",
                                        "第三条",
                                        "新增法条正文",
                                        0),
                                new UpdateLawArticleInput(
                                        first.articleId(),
                                        first.articleId(),
                                        first.number(),
                                        "修改后的第一条正文",
                                        1))),
                "admin-1");

        String thirdId = c3.articles().stream()
                .filter(article -> article.number().equals("第三条"))
                .findFirst()
                .orElseThrow()
                .articleId();
        LawDocument stored = lawRepository.findById(lawId).orElseThrow();
        assertThat(stored.getPendingChangeSet().getAddedArticleIds()).containsExactly(thirdId);
        assertThat(stored.getPendingChangeSet().getModifiedArticleIds())
                .containsExactly(first.articleId());
        assertThat(stored.getPendingChangeSet().getDeletedArticleIds()).containsExactly(secondId);
        assertThat(c3.articles()).extracting(LawDetailResponse.Article::articleId)
                .containsExactly(thirdId, first.articleId())
                .doesNotContain(secondId);
    }

    @Test
    void lawWithVersionHistoryIsSoftDeletedAndListedInRecycleBin() {
        InitialLawCreation creation = createLaw("历史软删除测试法");
        LawDetailResponse detail = queryService.getDetail(creation.law().getId());
        updateService(List.of()).updateLaw(
                creation.law().getId(),
                updateRequest(detail, detail.name(), "形成C2的正文"),
                "admin-1");

        recycleService(List.of()).deleteLaw(creation.law().getId());

        LawDocument deleted = lawRepository.findById(creation.law().getId()).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(contentVersionRepository.findByLawIdOrderBySeqAsc(creation.law().getId()))
                .hasSize(2);
        assertThat(queryService.list(null, 0, 10).items()).isEmpty();
        assertThat(queryService.listRecycle(null, 0, 10).items())
                .singleElement()
                .satisfies(item -> assertThat(item.id()).isEqualTo(creation.law().getId()));
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
                .isEqualTo("LAW.ACTIVE_TASK_EXISTS");
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
        LawDetailResponse detail = queryService.getDetail(creation.law().getId());
        updateService(List.of()).updateLaw(
                creation.law().getId(),
                updateRequest(detail, detail.name(), "形成历史以便软删除"),
                "admin-1");
        LawRecycleService recycleService = recycleService(List.of());
        recycleService.deleteLaw(creation.law().getId());

        LawDetailResponse restored = recycleService.restoreLaw(creation.law().getId());

        assertThat(restored.id()).isEqualTo(creation.law().getId());
        assertThat(lawRepository.findById(creation.law().getId()).orElseThrow().isDeleted())
                .isFalse();
        assertThat(queryService.list(null, 0, 10).items())
                .singleElement()
                .satisfies(item -> assertThat(item.id()).isEqualTo(creation.law().getId()));
        assertThat(queryService.listRecycle(null, 0, 10).items()).isEmpty();
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
                List.of());

        assertThatThrownBy(() -> service.restoreLaw("law-1"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(LawErrorCodes.NAME_ALREADY_EXISTS);
    }

    private static LawUpdateService updateService(List<LawMutationGuard> guards) {
        return new LawUpdateService(
                lawRepository,
                contentVersionRepository,
                lawAuditRepository,
                queryService,
                mongoTemplate,
                guards);
    }

    private static LawRecycleService recycleService(List<LawMutationGuard> guards) {
        return new LawRecycleService(
                lawRepository,
                queryService,
                mongoTemplate,
                guards);
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

    private static UpdateLawRequest updateRequest(
            LawDetailResponse detail,
            String name,
            String body) {
        LawDetailResponse.Article article = detail.articles().getFirst();
        return new UpdateLawRequest(
                new LawBaseInfoInput(
                        name,
                        detail.issuingAuthority(),
                        detail.publicationDate(),
                        detail.validityStatus()),
                List.of(),
                List.of(new UpdateLawArticleInput(
                        article.articleId(),
                        article.articleId(),
                        article.number(),
                        body,
                        article.order())));
    }
}
