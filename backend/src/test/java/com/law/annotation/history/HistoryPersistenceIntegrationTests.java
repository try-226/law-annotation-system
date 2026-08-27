package com.law.annotation.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.law.annotation.annotation.ArticleDraftValues;
import com.law.annotation.annotation.OverallDraftValues;
import com.law.annotation.annotation.TaskSubmissionDocument;
import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.ItemType;
import com.law.annotation.common.enums.ReviewItemState;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.field.FieldConfigSnapshot;
import com.law.annotation.field.FieldConfigSnapshotItem;
import com.law.annotation.history.dto.LawHistoryResponse;
import com.law.annotation.history.dto.TaskHistoryResponse;
import com.law.annotation.law.ArticleSnapshot;
import com.law.annotation.law.LawAuditDocument;
import com.law.annotation.law.LawAuditType;
import com.law.annotation.law.LawDocument;
import com.law.annotation.law.LawStructureNode;
import com.law.annotation.law.LawStructureNodeType;
import com.law.annotation.revision.RevisionMode;
import com.law.annotation.revision.RevisionScope;
import com.law.annotation.review.ReviewIssue;
import com.law.annotation.review.ReviewItemLocator;
import com.law.annotation.review.ReviewRoundDocument;
import com.law.annotation.review.ReviewRoundOutcome;
import com.law.annotation.review.ReviewRoundType;
import com.law.annotation.review.ReviewScopeType;
import com.law.annotation.task.TaskArticleSnapshot;
import com.law.annotation.task.TaskContentVersionSnapshot;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.task.TaskLawBaseInfoSnapshot;
import com.law.annotation.task.TaskStructureNodeSnapshot;
import com.law.annotation.user.UserDocument;
import com.law.annotation.version.AnnotationVersionDocument;
import com.law.annotation.version.ContentVersionDocument;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class HistoryPersistenceIntegrationTests {

    private static final Instant T0 = Instant.parse("2026-08-27T00:00:00Z");
    private static MongoServer mongoServer;
    private static MongoClient mongoClient;
    private static MongoTemplate mongoTemplate;
    private static HistoryService service;

    @BeforeAll
    static void startMongo() {
        mongoServer = new MongoServer(new MemoryBackend());
        mongoClient = MongoClients.create(mongoServer.bindAndGetConnectionString());
        mongoTemplate = new MongoTemplate(mongoClient, "history_pr20_test");
        service = new HistoryService(new HistoryQueryRepository(mongoTemplate));
    }

    @AfterAll
    static void stopMongo() {
        mongoClient.close();
        mongoServer.shutdown();
    }

    @BeforeEach
    void clearData() {
        mongoTemplate.remove(new Query(), LawDocument.class);
        mongoTemplate.remove(new Query(), ContentVersionDocument.class);
        mongoTemplate.remove(new Query(), LawAuditDocument.class);
        mongoTemplate.remove(new Query(), AnnotationVersionDocument.class);
        mongoTemplate.remove(new Query(), TaskDocument.class);
        mongoTemplate.remove(new Query(), TaskSubmissionDocument.class);
        mongoTemplate.remove(new Query(), ReviewRoundDocument.class);
        mongoTemplate.getCollection("field_config").deleteMany(new Document());
    }

    @Test
    void aggregatesDeletedLawHistoryAndKeepsTaskSnapshotsFrozenWithoutWrites() {
        ContentVersionDocument c1 = new ContentVersionDocument(
                "c-1", "law-1", 1,
                List.of(new ArticleSnapshot("article-1", "第一条", "历史正文", 0)),
                "admin-1", T0);
        ContentVersionDocument c2 = new ContentVersionDocument(
                "c-2", "law-1", 2,
                List.of(new ArticleSnapshot("article-1", "第一条", "现行正文", 0)),
                "admin-2", T0.plusSeconds(10));
        mongoTemplate.insert(c1);
        mongoTemplate.insert(c2);

        LawDocument currentLaw = LawDocument.createInitial(
                "law-1", "现行法律名称", "现行制定机关", LocalDate.of(2026, 8, 27),
                ValidityStatus.ACTIVE,
                List.of(new LawStructureNode(
                        "new-chapter", LawStructureNodeType.CHAPTER, "现行结构", null, 0,
                        List.of("article-1"))),
                "c-2", T0.plusSeconds(10));
        currentLaw.markDeleted(T0.plusSeconds(100));
        mongoTemplate.insert(currentLaw);
        mongoTemplate.insert(new LawAuditDocument(
                "audit-1", "law-1", LawAuditType.BASE_INFO,
                Map.of("name", "历史法律名称"), Map.of("name", "现行法律名称"),
                "admin-2", T0.plusSeconds(20)));
        mongoTemplate.insert(new LawAuditDocument(
                "audit-2", "law-1", LawAuditType.STRUCTURE,
                Map.of("structure", List.of(Map.of("title", "历史结构"))),
                Map.of("structure", List.of(Map.of("title", "现行结构"))),
                "admin-2", T0.plusSeconds(21)));

        ArticleDraftValues articleValues = new ArticleDraftValues(
                ItemType.OTHER, "关键词", "主体", null, "历史标注");
        AnnotationVersionDocument a1 = new AnnotationVersionDocument(
                "a-1", "law-1", 1, "c-1",
                new OverallDraftValues("法律", "关键词", "摘要", null),
                Map.of("article-1", articleValues), "task-1", "submission-1",
                "admin-2", T0.plusSeconds(60));
        mongoTemplate.insert(a1);

        TaskDocument task = new TaskDocument(
                "task-1", TaskType.ORDINARY, TaskState.CANCELED,
                "law-1", "annotator-1", "历史标注员", "历史任务", "历史备注",
                "c-1", new TaskContentVersionSnapshot(
                        "c-1", 1, List.of(new TaskArticleSnapshot(
                                "article-1", "第一条", "历史正文", 0))),
                new TaskLawBaseInfoSnapshot(
                        "历史法律名称", "历史制定机关", LocalDate.of(2020, 1, 1), ValidityStatus.ACTIVE),
                List.of(new TaskStructureNodeSnapshot(
                        "old-chapter", LawStructureNodeType.CHAPTER, "历史结构", null, 0,
                        List.of("article-1"))),
                new FieldConfigSnapshot(
                        List.of(new FieldConfigSnapshotItem("summary", false)),
                        List.of(new FieldConfigSnapshotItem("keywords", false))),
                "admin-1",
                "submission-1", "需求调整", "admin-1", T0.plusSeconds(90), T0.plusSeconds(30),
                T0.plusSeconds(90));
        mongoTemplate.insert(task);
        mongoTemplate.getCollection("field_config").insertOne(new Document()
                .append("fieldKey", "keywords")
                .append("required", true)
                .append("displayName", "现行配置"));
        mongoTemplate.insert(new TaskSubmissionDocument(
                "submission-1", "task-1", 1, 3,
                new OverallDraftValues("法律", "关键词", "摘要", null),
                Map.of("article-1", articleValues), "annotator-1", T0.plusSeconds(40)));
        ReviewItemLocator articleLocator = ReviewItemLocator.article("article-1");
        ReviewIssue issue = new ReviewIssue(
                "round-1", "task-1", ReviewScopeType.ARTICLE, "article-1", "需要修改", T0.plusSeconds(50));
        mongoTemplate.insert(new ReviewRoundDocument(
                "round-1", "task-1", "law-1", 1, ReviewRoundType.INITIAL_REVIEW,
                "submission-1", null, "admin-2", List.of(articleLocator),
                Map.of(articleLocator.storageKey(), ReviewItemState.CHECKED),
                Map.of(articleLocator.storageKey(), issue), 1, 1, 0, 0,
                ReviewRoundOutcome.APPROVED, T0.plusSeconds(55), "a-1",
                T0.plusSeconds(45), T0.plusSeconds(46), T0.plusSeconds(60)));

        long contentBefore = mongoTemplate.count(new Query(), ContentVersionDocument.class);
        long taskBefore = mongoTemplate.count(new Query(), TaskDocument.class);

        LawHistoryResponse timeline = service.getLawHistory("law-1", principal("admin-1", Role.ADMIN));
        TaskHistoryResponse historyTask = service.getTask(
                "law-1", "task-1", principal("annotator-1", Role.ANNOTATOR));

        assertThat(timeline.deleted()).isTrue();
        assertThat(timeline.timeline()).extracting(item -> item.type()).contains(
                HistoryItemType.CONTENT_VERSION_CREATED,
                HistoryItemType.LAW_METADATA_CHANGED,
                HistoryItemType.LAW_STRUCTURE_CHANGED,
                HistoryItemType.ANNOTATION_VERSION_APPROVED,
                HistoryItemType.TASK_CREATED,
                HistoryItemType.TASK_SUBMITTED,
                HistoryItemType.REVIEW_STARTED,
                HistoryItemType.REVIEW_ISSUE_CREATED,
                HistoryItemType.REVIEW_COMPLETED,
                HistoryItemType.TASK_CANCELED);
        assertThat(historyTask.lawDeleted()).isTrue();
        assertThat(historyTask.lawBaseInfoSnapshot().name()).isEqualTo("历史法律名称");
        assertThat(historyTask.contentVersionSnapshot().contentVersionId()).isEqualTo("c-1");
        assertThat(historyTask.contentVersionSnapshot().articles().getFirst().body()).isEqualTo("历史正文");
        assertThat(historyTask.structureSnapshot().getFirst().title()).isEqualTo("历史结构");
        assertThat(historyTask.fieldConfigSnapshot().article().getFirst().required()).isFalse();
        assertThat(historyTask.cancelReason()).isEqualTo("需求调整");
        assertThat(historyTask.canceledBy()).isEqualTo("admin-1");
        assertThat(historyTask.canceledAt()).isEqualTo(T0.plusSeconds(90));
        assertThat(historyTask.submissions()).hasSize(1);
        assertThat(historyTask.reviewRounds()).hasSize(1);
        assertThat(service.getContentVersion("law-1", "c-1", principal("admin-1", Role.ADMIN))
                .semanticArticlesSnapshot().getFirst().getBody()).isEqualTo("历史正文");
        assertThat(service.getAnnotationVersion("law-1", "a-1", principal("admin-1", Role.ADMIN))
                .contentVersionId()).isEqualTo("c-1");
        assertThat(service.getAudit("law-1", "audit-1", principal("admin-1", Role.ADMIN))
                .before()).containsEntry("name", "历史法律名称");
        assertThat(service.getAudit("law-1", "audit-2", principal("admin-1", Role.ADMIN))
                .auditType()).isEqualTo(LawAuditType.STRUCTURE);
        assertThat(mongoTemplate.count(new Query(), ContentVersionDocument.class)).isEqualTo(contentBefore);
        assertThat(mongoTemplate.count(new Query(), TaskDocument.class)).isEqualTo(taskBefore);
    }

    @Test
    void hidesWrongLawAndOtherAnnotatorsTask() {
        mongoTemplate.insert(LawDocument.createInitial(
                "law-1", "测试法", "制定机关", LocalDate.of(2026, 8, 27),
                ValidityStatus.ACTIVE, List.of(), "c-1", T0));
        mongoTemplate.insert(new ContentVersionDocument(
                "c-1", "law-1", 1, List.of(), "admin-1", T0));
        mongoTemplate.insert(new TaskDocument(
                "task-1", TaskType.ORDINARY, TaskState.PENDING_ANNOTATION,
                "law-1", "annotator-1", "标注员", "任务", null,
                "c-1", new TaskContentVersionSnapshot("c-1", 1, List.of()),
                new TaskLawBaseInfoSnapshot(
                        "测试法", "制定机关", LocalDate.of(2026, 8, 27), ValidityStatus.ACTIVE),
                List.of(), new FieldConfigSnapshot(List.of(), List.of()), "admin-1",
                null, null, null, null, T0, T0));

        assertThatThrownBy(() -> service.getTask(
                "law-1", "task-1", principal("annotator-2", Role.ANNOTATOR)))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getStatus().value()).isEqualTo(404));
        assertThatThrownBy(() -> service.getContentVersion(
                "law-other", "c-1", principal("admin-1", Role.ADMIN)))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getStatus().value()).isEqualTo(404));
    }

    @Test
    void keepsC1C2C3AndA1A2A3BoundToTheirOwnHistoricalSnapshots() {
        mongoTemplate.insert(LawDocument.createInitial(
                "law-1", "现行法", "制定机关", LocalDate.of(2026, 8, 27),
                ValidityStatus.ACTIVE, List.of(), "c-3", T0));
        for (int seq = 1; seq <= 3; seq++) {
            mongoTemplate.insert(new ContentVersionDocument(
                    "c-" + seq, "law-1", seq,
                    List.of(new ArticleSnapshot(
                            "article-1", "第一条", "C" + seq + "正文", 0)),
                    "admin-" + seq, T0.plusSeconds(seq)));
            mongoTemplate.insert(new AnnotationVersionDocument(
                    "a-" + seq, "law-1", seq, "c-" + seq,
                    new OverallDraftValues("法律", "A" + seq, "A" + seq + "摘要", null),
                    Map.of("article-1", new ArticleDraftValues(
                            ItemType.OTHER, "A" + seq + "关键词", null, null, null)),
                    "task-a-" + seq, "submission-a-" + seq,
                    "reviewer-" + seq, T0.plusSeconds(10 + seq)));
        }

        LawHistoryResponse timeline = service.getLawHistory("law-1", principal("admin-1", Role.ADMIN));

        assertThat(timeline.timeline().stream()
                .filter(item -> item.type() == HistoryItemType.CONTENT_VERSION_CREATED))
                .extracting(item -> item.entityId())
                .containsExactlyInAnyOrder("c-1", "c-2", "c-3");
        assertThat(timeline.timeline().stream()
                .filter(item -> item.type() == HistoryItemType.ANNOTATION_VERSION_APPROVED))
                .extracting(item -> item.entityId())
                .containsExactlyInAnyOrder("a-1", "a-2", "a-3");
        assertThat(service.getContentVersion("law-1", "c-1", principal("admin-1", Role.ADMIN))
                .semanticArticlesSnapshot().getFirst().getBody()).isEqualTo("C1正文");
        assertThat(service.getContentVersion("law-1", "c-3", principal("admin-1", Role.ADMIN))
                .semanticArticlesSnapshot().getFirst().getBody()).isEqualTo("C3正文");
        assertThat(service.getAnnotationVersion("law-1", "a-1", principal("admin-1", Role.ADMIN))
                .articleResults().getFirst().values().keywords()).isEqualTo("A1关键词");
        assertThat(service.getAnnotationVersion("law-1", "a-3", principal("admin-1", Role.ADMIN))
                .contentVersionId()).isEqualTo("c-3");
        assertThat(mongoTemplate.count(new Query(), ContentVersionDocument.class)).isEqualTo(3);
        assertThat(mongoTemplate.count(new Query(), AnnotationVersionDocument.class)).isEqualTo(3);
    }

    @Test
    void returnsRevisionScopeRereviewIssuesAndIndependentRevisionCancellation() {
        mongoTemplate.insert(LawDocument.createInitial(
                "law-1", "修订测试法", "制定机关", LocalDate.of(2026, 8, 27),
                ValidityStatus.ACTIVE, List.of(), "c-2", T0));
        mongoTemplate.insert(new ContentVersionDocument(
                "c-2", "law-1", 2,
                List.of(new ArticleSnapshot("article-1", "第一条", "修订正文", 0)),
                "admin-1", T0));
        RevisionScope scope = new RevisionScope(
                RevisionMode.CONTENT_CHANGE, true, List.of("article-1"), List.of("article-1"));
        TaskDocument revision = new TaskDocument(
                "revision-1", TaskType.REVISION, TaskState.APPROVED,
                "law-1", "annotator-1", "修订标注员", "修订任务", null,
                "c-2", new TaskContentVersionSnapshot(
                        "c-2", 2, List.of(new TaskArticleSnapshot(
                                "article-1", "第一条", "修订正文", 0))),
                new TaskLawBaseInfoSnapshot(
                        "修订测试法", "制定机关", LocalDate.of(2026, 8, 27), ValidityStatus.ACTIVE),
                List.of(), new FieldConfigSnapshot(List.of(), List.of()),
                "a-1", scope, "admin-1", "submission-1",
                null, null, null, T0.plusSeconds(1), T0.plusSeconds(8));
        mongoTemplate.insert(revision);
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is("revision-1")),
                new Update()
                        .set("currentSubmissionId", "submission-2")
                        .set("currentReviewRoundId", "round-2")
                        .set("approvedAnnotationVersionId", "a-2"),
                TaskDocument.class);
        ArticleDraftValues values = new ArticleDraftValues(
                ItemType.OTHER, "修订关键词", null, null, null);
        mongoTemplate.insert(new TaskSubmissionDocument(
                "submission-1", "revision-1", 1, 1,
                new OverallDraftValues("法律", null, null, null),
                Map.of("article-1", values), "annotator-1", T0.plusSeconds(2)));
        ReviewItemLocator locator = ReviewItemLocator.article("article-1");
        mongoTemplate.insert(new TaskSubmissionDocument(
                "submission-2", "revision-1", 2, 2,
                new OverallDraftValues("法律", null, null, null),
                Map.of("article-1", values), "round-1", List.of(locator),
                "annotator-1", T0.plusSeconds(5)));
        ReviewIssue issue = new ReviewIssue(
                "round-1", "revision-1", ReviewScopeType.ARTICLE,
                "article-1", "修订项仍需调整", T0.plusSeconds(3));
        mongoTemplate.insert(new ReviewRoundDocument(
                "round-1", "revision-1", "law-1", 1, ReviewRoundType.INITIAL_REVIEW,
                "submission-1", null, "reviewer-1", List.of(locator),
                Map.of(locator.storageKey(), ReviewItemState.NEEDS_CHANGE),
                Map.of(locator.storageKey(), issue), 1, 1, 0, 1,
                ReviewRoundOutcome.PARTIALLY_REJECTED, T0.plusSeconds(4), null,
                T0.plusSeconds(2), T0.plusSeconds(2), T0.plusSeconds(4)));
        mongoTemplate.insert(new ReviewRoundDocument(
                "round-2", "revision-1", "law-1", 2, ReviewRoundType.REREVIEW,
                "submission-2", "submission-1", "reviewer-2", List.of(locator),
                Map.of(locator.storageKey(), ReviewItemState.CHECKED), Map.of(),
                1, 1, 0, 0, ReviewRoundOutcome.APPROVED, T0.plusSeconds(7), "a-2",
                T0.plusSeconds(6), T0.plusSeconds(6), T0.plusSeconds(7)));

        TaskDocument canceledRevision = new TaskDocument(
                "revision-canceled", TaskType.REVISION, TaskState.CANCELED,
                "law-1", "annotator-2", "取消标注员", "取消的修订任务", null,
                "c-2", new TaskContentVersionSnapshot(
                        "c-2", 2, List.of(new TaskArticleSnapshot(
                                "article-1", "第一条", "修订正文", 0))),
                new TaskLawBaseInfoSnapshot(
                        "修订测试法", "制定机关", LocalDate.of(2026, 8, 27), ValidityStatus.ACTIVE),
                List.of(), new FieldConfigSnapshot(List.of(), List.of()),
                "a-1", scope, "admin-1", null,
                "取消修订", "admin-2", T0.plusSeconds(9), T0.plusSeconds(8), T0.plusSeconds(9));
        mongoTemplate.insert(canceledRevision);

        TaskHistoryResponse detail = service.getTask(
                "law-1", "revision-1", principal("admin-1", Role.ADMIN));
        TaskHistoryResponse canceled = service.getTask(
                "law-1", "revision-canceled", principal("admin-1", Role.ADMIN));

        assertThat(detail.taskType()).isEqualTo(TaskType.REVISION);
        assertThat(detail.baseAnnotationVersionId()).isEqualTo("a-1");
        assertThat(detail.revisionScope()).isEqualTo(scope);
        assertThat(detail.approvedAnnotationVersionId()).isEqualTo("a-2");
        assertThat(detail.submissions()).extracting(TaskHistoryResponse.Submission::submissionNo)
                .containsExactly(1, 2);
        assertThat(detail.submissions().get(1).sourceReviewRoundId()).isEqualTo("round-1");
        assertThat(detail.reviewRounds()).extracting(TaskHistoryResponse.ReviewRound::roundType)
                .containsExactly(ReviewRoundType.INITIAL_REVIEW, ReviewRoundType.REREVIEW);
        assertThat(detail.reviewRounds().getFirst().issues().getFirst().locator()).isEqualTo(locator);
        assertThat(detail.reviewRounds().getFirst().issues().getFirst().actorId()).isEqualTo("reviewer-1");
        assertThat(canceled.taskState()).isEqualTo(TaskState.CANCELED);
        assertThat(canceled.cancelReason()).isEqualTo("取消修订");
        assertThat(canceled.canceledBy()).isEqualTo("admin-2");
    }

    private static UserPrincipal principal(String id, Role role) {
        UserDocument user = new UserDocument(
                "用户", id, id, "$2a$12$hash", role, true, T0, T0);
        user.setId(id);
        return UserPrincipal.from(user);
    }
}
