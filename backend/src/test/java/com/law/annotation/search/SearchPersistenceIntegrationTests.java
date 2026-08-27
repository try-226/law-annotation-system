package com.law.annotation.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.law.annotation.annotation.ArticleDraftValues;
import com.law.annotation.annotation.OverallDraftValues;
import com.law.annotation.annotation.TaskDraftDocument;
import com.law.annotation.annotation.TaskDraftRepository;
import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.ItemType;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.law.ArticleSnapshot;
import com.law.annotation.law.LawDocument;
import com.law.annotation.law.LawDomainRules;
import com.law.annotation.law.LawRepository;
import com.law.annotation.law.LawStructureNode;
import com.law.annotation.law.LawStructureNodeType;
import com.law.annotation.law.PendingChangeSet;
import com.law.annotation.task.TaskArticleSnapshot;
import com.law.annotation.task.TaskContentVersionSnapshot;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.task.TaskLawBaseInfoSnapshot;
import com.law.annotation.task.TaskRepository;
import com.law.annotation.task.TaskStructureNodeSnapshot;
import com.law.annotation.user.UserDocument;
import com.law.annotation.version.AnnotationVersionDocument;
import com.law.annotation.version.AnnotationVersionRepository;
import com.law.annotation.version.ContentVersionDocument;
import com.law.annotation.version.ContentVersionRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;

class SearchPersistenceIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-08-27T00:00:00Z");

    private static MongoServer mongoServer;
    private static MongoClient mongoClient;
    private static MongoTemplate mongoTemplate;
    private static LawRepository lawRepository;
    private static ContentVersionRepository contentVersionRepository;
    private static AnnotationVersionRepository annotationVersionRepository;
    private static TaskRepository taskRepository;
    private static TaskDraftRepository taskDraftRepository;
    private static SearchRepository searchRepository;
    private static SearchService service;

    @BeforeAll
    static void startMongo() {
        mongoServer = new MongoServer(new MemoryBackend());
        mongoClient = MongoClients.create(mongoServer.bindAndGetConnectionString());
        mongoTemplate = new MongoTemplate(mongoClient, "law_pr18_search_test");
        MongoRepositoryFactory factory = new MongoRepositoryFactory(mongoTemplate);
        lawRepository = factory.getRepository(LawRepository.class);
        contentVersionRepository = factory.getRepository(ContentVersionRepository.class);
        annotationVersionRepository = factory.getRepository(AnnotationVersionRepository.class);
        taskRepository = factory.getRepository(TaskRepository.class);
        taskDraftRepository = factory.getRepository(TaskDraftRepository.class);
        searchRepository = new SearchRepository(mongoTemplate);
        service = new SearchService(
                searchRepository,
                contentVersionRepository,
                annotationVersionRepository,
                taskRepository,
                taskDraftRepository);
    }

    @AfterAll
    static void stopMongo() {
        mongoClient.close();
        mongoServer.shutdown();
    }

    @BeforeEach
    void seedDocuments() {
        mongoTemplate.remove(new Query(), LawDocument.class);
        mongoTemplate.remove(new Query(), ContentVersionDocument.class);
        mongoTemplate.remove(new Query(), AnnotationVersionDocument.class);
        mongoTemplate.remove(new Query(), TaskDocument.class);
        mongoTemplate.remove(new Query(), TaskDraftDocument.class);

        contentVersionRepository.insert(version(
                "content-old", "law-1", "任务绑定旧正文", 1));
        contentVersionRepository.insert(version(
                "content-current",
                "law-1",
                "NEW_CONTENT_ONLY 当前新正文.字面符号 * [ ( + ? 行政机关 应当 依法 处理",
                2));
        contentVersionRepository.insert(version(
                "content-deleted", "law-deleted", "已删除秘密正文", 1));

        annotationVersionRepository.insert(annotation(
                "annotation-old",
                "content-old",
                "OLD_ANNOTATION_ONLY",
                "历史法条秘密"));
        annotationVersionRepository.insert(annotation(
                "annotation-current", "content-current", "当前正式摘要", "当前正式备注"));

        lawRepository.save(law(
                "law-1",
                "当前有效法律",
                "content-current",
                "annotation-current",
                false));
        LawDocument deleted = law(
                "law-deleted",
                "已删除秘密法律",
                "content-deleted",
                null,
                false);
        deleted.markDeleted(NOW.plusSeconds(60));
        lawRepository.save(deleted);

        taskRepository.save(task());
        taskDraftRepository.save(new TaskDraftDocument(
                "task-1",
                new OverallDraftValues("草稿类别", "草稿关键词", "草稿秘密摘要", null),
                Map.of("article-1", new ArticleDraftValues(
                        ItemType.PROCEDURE,
                        "任务草稿关键词",
                        "任务主体",
                        null,
                        "任务草稿秘密")),
                2,
                "annotator-1",
                NOW,
                NOW));
    }

    @Test
    void persistedAdminSearchExcludesHistoricalADeletedLawAndTaskDraft() {
        assertThat(service.searchLaws(
                "当前有效法律", SearchScope.LAW_TEXT, 0, 10).items()).singleElement()
                .satisfies(hit -> assertThat(hit.hitField()).isEqualTo("law.name"));
        assertThat(service.searchLaws(
                "制定机关", SearchScope.LAW_TEXT, 0, 10).items()).singleElement()
                .satisfies(hit -> assertThat(hit.hitField()).isEqualTo("law.issuingAuthority"));
        assertThat(service.searchLaws(
                "第一章", SearchScope.LAW_TEXT, 0, 10).items()).singleElement()
                .satisfies(hit -> assertThat(hit.hitField()).isEqualTo("structure.title"));
        assertThat(service.searchLaws(
                "第一条", SearchScope.LAW_TEXT, 0, 10).items()).singleElement()
                .satisfies(hit -> assertThat(hit.hitField()).isEqualTo("article.number"));
        assertThat(service.searchLaws(
                "当前新正文", SearchScope.ALL, 0, 10).items()).singleElement()
                .satisfies(hit -> assertThat(hit.hitField()).isEqualTo("article.body"));
        assertThat(service.searchLaws(
                "当前正式摘要", SearchScope.ALL, 0, 10).items()).singleElement()
                .satisfies(hit -> assertThat(hit.hitField())
                        .isEqualTo("overallAnnotation.summary"));
        assertThat(service.searchLaws(
                "当前正式备注", SearchScope.ALL, 0, 10).items()).singleElement()
                .satisfies(hit -> assertThat(hit.hitField())
                        .isEqualTo("articleAnnotation.annotationNote"));
        assertThat(service.searchLaws(
                "权利义务类", SearchScope.ANNOTATION, 0, 10).items()).singleElement()
                .satisfies(hit -> assertThat(hit.hitField())
                        .isEqualTo("articleAnnotation.itemType"));
        assertThat(service.searchLaws("历史秘密", SearchScope.ALL, 0, 10).items()).isEmpty();
        assertThat(service.searchLaws("草稿秘密", SearchScope.ALL, 0, 10).items()).isEmpty();
        assertThat(service.searchLaws("已删除秘密", SearchScope.ALL, 0, 10).items()).isEmpty();

        for (String literal : List.of(".", "*", "[", "(", "+", "?")) {
            assertThat(service.searchLaws(
                    literal, SearchScope.LAW_TEXT, 0, 10).items())
                    .singleElement()
                    .satisfies(hit -> assertThat(hit.snippet().substring(
                            hit.highlightStart(), hit.highlightEnd())).isEqualTo(literal));
        }
        assertThat(service.searchLaws(
                " 行政机关\r\n应当  依法\t处理 ",
                SearchScope.LAW_TEXT,
                0,
                10).items()).singleElement();
    }

    @Test
    void persistedMongoAggregationFiltersLawTextAndAnnotationCandidates() {
        Pattern textPattern = Pattern.compile(Pattern.quote("NEW_CONTENT_ONLY"));
        assertThat(searchRepository.findVisibleLawsMatching(
                textPattern, SearchScope.LAW_TEXT))
                .extracting(LawDocument::getId)
                .containsExactly("law-1");

        Pattern annotationPattern = Pattern.compile(Pattern.quote("当前正式摘要"));
        assertThat(searchRepository.findVisibleLawsMatching(
                annotationPattern, SearchScope.ANNOTATION))
                .extracting(LawDocument::getId)
                .containsExactly("law-1");
    }

    @Test
    void persistedSemanticMismatchHidesOldAWithoutBlockingCurrentC() {
        lawRepository.save(law(
                "law-1",
                "当前有效法律",
                "content-current",
                "annotation-old",
                true));

        assertThat(service.searchLaws(
                "OLD_ANNOTATION_ONLY", SearchScope.ANNOTATION, 0, 10).items()).isEmpty();
        assertThat(service.searchLaws(
                "OLD_ANNOTATION_ONLY", SearchScope.ALL, 0, 10).items()).isEmpty();
        assertThat(service.searchLaws(
                "NEW_CONTENT_ONLY", SearchScope.LAW_TEXT, 0, 10).items())
                .singleElement()
                .satisfies(hit -> assertThat(hit.hitField()).isEqualTo("article.body"));
        assertThat(service.searchLaws(
                "NEW_CONTENT_ONLY", SearchScope.ALL, 0, 10).items())
                .singleElement()
                .satisfies(hit -> assertThat(hit.hitField()).isEqualTo("article.body"));
    }

    @Test
    void persistedAnnotationOnlyRevisionCanSearchNewAOnUnchangedC() {
        annotationVersionRepository.insert(annotation(
                "annotation-revised",
                "content-current",
                "ANNOTATION_ONLY_A2",
                "修订后法条备注"));
        lawRepository.save(law(
                "law-1",
                "当前有效法律",
                "content-current",
                "annotation-revised",
                false));

        assertThat(service.searchLaws(
                "ANNOTATION_ONLY_A2", SearchScope.ANNOTATION, 0, 10).items())
                .singleElement()
                .satisfies(hit -> assertThat(hit.hitField())
                        .isEqualTo("overallAnnotation.summary"));
        assertThat(service.searchLaws(
                "OLD_ANNOTATION_ONLY", SearchScope.ANNOTATION, 0, 10).items()).isEmpty();
    }

    @Test
    void persistedAllSearchKeepsUnannotatedLawTextAndStablePagination() {
        contentVersionRepository.insert(version(
                "content-page-2", "law-2", "PAGED_MATCH", 1));
        contentVersionRepository.insert(version(
                "content-page-3", "law-3", "PAGED_MATCH", 1));
        lawRepository.save(law(
                "law-2", "分页法律二", "content-page-2", null, false));
        lawRepository.save(law(
                "law-3", "分页法律三", "content-page-3", null, false));

        var first = service.searchLaws("PAGED_MATCH", SearchScope.ALL, 0, 1);
        var second = service.searchLaws("PAGED_MATCH", SearchScope.ALL, 1, 1);

        assertThat(first.totalElements()).isEqualTo(2);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.items()).singleElement()
                .extracting(hit -> hit.lawId()).isEqualTo("law-3");
        assertThat(second.items()).singleElement()
                .extracting(hit -> hit.lawId()).isEqualTo("law-2");
    }

    @Test
    void persistedTaskSearchUsesOwnerBoundSnapshotAndSavedDraftOnly() {
        UserPrincipal owner = principal("annotator-1");
        assertThat(service.searchTask(
                "task-1", "任务绑定旧正文", SearchScope.LAW_TEXT, 0, 10, owner).items())
                .singleElement()
                .satisfies(hit -> assertThat(hit.hitField()).isEqualTo("article.body"));
        assertThat(service.searchTask(
                "task-1", "当前新正文", SearchScope.LAW_TEXT, 0, 10, owner).items())
                .isEmpty();
        assertThat(service.searchTask(
                "task-1", "草稿秘密", SearchScope.ANNOTATION, 0, 10, owner).items())
                .hasSize(2);

        assertThatThrownBy(() -> service.searchTask(
                        "task-1",
                        "任务绑定旧正文",
                        SearchScope.ALL,
                        0,
                        10,
                        principal("annotator-2")))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("TASK.NOT_FOUND");
    }

    private static ContentVersionDocument version(
            String id,
            String lawId,
            String body,
            int seq) {
        return new ContentVersionDocument(
                id,
                lawId,
                seq,
                List.of(new ArticleSnapshot("article-1", "第一条", body, 0)),
                "admin-1",
                NOW);
    }

    private static AnnotationVersionDocument annotation(
            String id,
            String contentVersionId,
            String summary,
            String note) {
        return new AnnotationVersionDocument(
                id,
                "law-1",
                id.equals("annotation-old") ? 1 : id.equals("annotation-current") ? 2 : 3,
                contentVersionId,
                new OverallDraftValues("行政法", "正式关键词", summary, null),
                Map.of("article-1", new ArticleDraftValues(
                        ItemType.RIGHTS_DUTIES,
                        "正式法条关键词",
                        "公民",
                        null,
                        note)),
                "task-approved",
                "submission-approved",
                "reviewer-1",
                NOW);
    }

    private static LawDocument law(
            String id,
            String name,
            String contentVersionId,
            String annotationVersionId,
            boolean pendingRevision) {
        return new LawDocument(
                id,
                name,
                LawDomainRules.normalizeLawName(name),
                "制定机关",
                LocalDate.of(2026, 8, 19),
                ValidityStatus.ACTIVE,
                List.of(new LawStructureNode(
                        "chapter-1",
                        LawStructureNodeType.CHAPTER,
                        "第一章",
                        null,
                        0,
                        List.of("article-1"))),
                null,
                contentVersionId,
                annotationVersionId,
                pendingRevision,
                pendingRevision
                        ? PendingChangeSet.empty().recordModification("article-1")
                        : PendingChangeSet.empty(),
                NOW,
                NOW);
    }

    private static TaskDocument task() {
        return new TaskDocument(
                "task-1",
                TaskType.ORDINARY,
                TaskState.ANNOTATING,
                "law-1",
                "annotator-1",
                "标注员一",
                "普通任务",
                null,
                "content-old",
                new TaskContentVersionSnapshot(
                        "content-old",
                        1,
                        List.of(new TaskArticleSnapshot(
                                "article-1", "第一条", "任务绑定旧正文", 0))),
                new TaskLawBaseInfoSnapshot(
                        "任务快照法律",
                        "任务制定机关",
                        LocalDate.of(2026, 8, 19),
                        ValidityStatus.ACTIVE),
                List.of(new TaskStructureNodeSnapshot(
                        "chapter-1",
                        LawStructureNodeType.CHAPTER,
                        "任务第一章",
                        null,
                        0,
                        List.of("article-1"))),
                null,
                "admin-1",
                null,
                null,
                null,
                null,
                NOW,
                NOW);
    }

    private static UserPrincipal principal(String id) {
        UserDocument user = new UserDocument(
                "标注员", id, id, "$2a$12$hash", Role.ANNOTATOR, true, NOW, NOW);
        user.setId(id);
        return UserPrincipal.from(user);
    }
}
