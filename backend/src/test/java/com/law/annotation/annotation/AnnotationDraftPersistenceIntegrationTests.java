package com.law.annotation.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.law.annotation.annotation.dto.SaveArticleDraftRequest;
import com.law.annotation.annotation.dto.SaveOverallDraftRequest;
import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.field.FieldConfigDocument;
import com.law.annotation.field.FieldConfigRepository;
import com.law.annotation.field.FieldConfigService;
import com.law.annotation.field.FieldConfigSnapshot;
import com.law.annotation.field.FieldConfigSnapshotItem;
import com.law.annotation.task.TaskArticleSnapshot;
import com.law.annotation.task.TaskContentVersionSnapshot;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.task.TaskLawBaseInfoSnapshot;
import com.law.annotation.task.TaskRepository;
import com.law.annotation.task.dto.TaskDetailResponse;
import com.law.annotation.user.UserDocument;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;

class AnnotationDraftPersistenceIntegrationTests {

    private static MongoServer mongoServer;
    private static MongoClient mongoClient;
    private static MongoTemplate mongoTemplate;
    private static TaskRepository taskRepository;
    private static AnnotationDraftRepository draftRepository;
    private static FieldConfigRepository fieldConfigRepository;
    private static FieldConfigService fieldConfigService;
    private static AnnotationDraftService annotationDraftService;

    @BeforeAll
    static void startMongo() throws Exception {
        mongoServer = new MongoServer(new MemoryBackend());
        mongoClient = MongoClients.create(mongoServer.bindAndGetConnectionString());
        mongoTemplate = new MongoTemplate(mongoClient, "annotation_draft_test");
        MongoRepositoryFactory factory = new MongoRepositoryFactory(mongoTemplate);
        taskRepository = factory.getRepository(TaskRepository.class);
        draftRepository = factory.getRepository(AnnotationDraftRepository.class);
        fieldConfigRepository = factory.getRepository(FieldConfigRepository.class);
        fieldConfigService = new FieldConfigService(fieldConfigRepository);
        annotationDraftService = new AnnotationDraftService(
                taskRepository, draftRepository, mongoTemplate);
    }

    @AfterAll
    static void stopMongo() {
        mongoClient.close();
        mongoServer.shutdown();
    }

    @BeforeEach
    void clearData() {
        mongoTemplate.remove(new Query(), AnnotationDraftDocument.class);
        mongoTemplate.remove(new Query(), TaskDocument.class);
        mongoTemplate.remove(new Query(), FieldConfigDocument.class);
    }

    @Test
    void onlyAssignedAnnotatorCanAccessTaskDraft() {
        taskRepository.insert(task(TaskState.ANNOTATING, defaultSnapshot()));

        assertThatThrownBy(() -> annotationDraftService.getWorkbench(
                        "task-1", principal("annotator-2", Role.ANNOTATOR)))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.getStatus().value()).isEqualTo(403);
                    assertThat(apiException.getCode())
                            .isEqualTo(AnnotationErrorCodes.NOT_TASK_OWNER);
                });
        assertThat(draftRepository.count()).isZero();
    }

    @Test
    void draftCanBeSavedOnlyWhileTaskIsAnnotating() {
        taskRepository.insert(task(TaskState.ANNOTATING, defaultSnapshot()));
        UserPrincipal owner = principal("annotator-1", Role.ANNOTATOR);

        assertThat(annotationDraftService.getWorkbench("task-1", owner)
                        .task().contentVersionSnapshot().articles())
                .singleElement()
                .satisfies(article -> {
                    assertThat(article.articleId()).isEqualTo("article-1");
                    assertThat(article.body()).isEqualTo("任务创建时正文");
                });

        annotationDraftService.saveOverall(
                "task-1",
                new SaveOverallDraftRequest(
                        " 行政 ", " 监管，处罚 ", null, " 初稿 "),
                owner);
        AnnotationDraftDocument saved = draftRepository.findById("task-1").orElseThrow();
        assertThat(saved.getOverallFields().lawCategory()).isEqualTo("行政");
        assertThat(saved.getOverallFields().overallKeywords()).isEqualTo("监管,处罚");
        assertThat(saved.getOverallFields().overallNote()).isEqualTo("初稿");

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is("task-1")),
                new Update().set("taskState", TaskState.PENDING_REVIEW),
                TaskDocument.class);

        assertCode(
                () -> annotationDraftService.saveOverall(
                        "task-1",
                        new SaveOverallDraftRequest(
                                "民事", "合同", null, "不应保存"),
                        owner),
                AnnotationErrorCodes.INVALID_TASK_STATE);
        assertThat(draftRepository.findById("task-1").orElseThrow()
                        .getOverallFields().overallNote())
                .isEqualTo("初稿");
    }

    @Test
    void historicalTaskStillUsesItsSnapshotAfterCurrentConfigChanges() {
        fieldConfigService.updateRequired(
                "summary", true, "admin-1", Role.ADMIN);
        FieldConfigSnapshot creationSnapshot = fieldConfigService.getCurrentSnapshot();
        taskRepository.insert(task(TaskState.ANNOTATING, creationSnapshot));

        fieldConfigService.updateRequired(
                "summary", false, "admin-1", Role.ADMIN);
        assertThat(required(
                fieldConfigService.getCurrentSnapshot().overall(), "summary")).isFalse();

        annotationDraftService.saveOverall(
                "task-1",
                new SaveOverallDraftRequest("行政", "监管", null, null),
                principal("annotator-1", Role.ANNOTATOR));
        annotationDraftService.saveArticle(
                "task-1",
                "article-1",
                new SaveArticleDraftRequest(
                        "PROHIBITION_RESTRICTION", "处罚", null, null, null),
                principal("annotator-1", Role.ANNOTATOR));

        assertThatThrownBy(() -> annotationDraftService.submitReview(
                        "task-1", principal("annotator-1", Role.ANNOTATOR)))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getLocators())
                        .anySatisfy(locator -> {
                            assertThat(locator.path()).isEqualTo("overall.summary");
                            assertThat(locator.message()).isEqualTo("required");
                        }));
        assertThat(taskRepository.findById("task-1").orElseThrow().getTaskState())
                .isEqualTo(TaskState.ANNOTATING);
    }

    @Test
    void requiredArticleFieldBlocksSubmitThenSingleConditionalSubmitSucceeds() {
        taskRepository.insert(task(TaskState.ANNOTATING, defaultSnapshot()));
        UserPrincipal owner = principal("annotator-1", Role.ANNOTATOR);
        annotationDraftService.saveOverall(
                "task-1",
                new SaveOverallDraftRequest("行政", "监管", null, null),
                owner);
        annotationDraftService.saveArticle(
                "task-1",
                "article-1",
                new SaveArticleDraftRequest("LIABILITY", null, null, null, null),
                owner);

        assertThatThrownBy(() -> annotationDraftService.submitReview("task-1", owner))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getLocators())
                        .anySatisfy(locator -> {
                            assertThat(locator.path()).isEqualTo("article[1].keywords");
                            assertThat(locator.message()).isEqualTo("required");
                        }));

        annotationDraftService.saveArticle(
                "task-1",
                "article-1",
                new SaveArticleDraftRequest("LIABILITY", "罚款", null, null, null),
                owner);
        TaskDetailResponse submitted = annotationDraftService.submitReview("task-1", owner);
        assertThat(submitted.taskState()).isEqualTo(TaskState.PENDING_REVIEW);
        assertThat(draftRepository.count()).isEqualTo(1);

        assertCode(
                () -> annotationDraftService.submitReview("task-1", owner),
                AnnotationErrorCodes.INVALID_TASK_STATE);
        assertThat(draftRepository.count()).isEqualTo(1);
    }

    @Test
    void saveValidatesEnumKeywordFormatAndBoundArticleBeforeWriting() {
        taskRepository.insert(task(TaskState.ANNOTATING, defaultSnapshot()));
        UserPrincipal owner = principal("annotator-1", Role.ANNOTATOR);

        assertCode(
                () -> annotationDraftService.saveOverall(
                        "task-1",
                        new SaveOverallDraftRequest("未知类别", "监管,,处罚", null, null),
                        owner),
                AnnotationErrorCodes.VALIDATION_FAILED);
        assertCode(
                () -> annotationDraftService.saveArticle(
                        "task-1",
                        "article-outside-snapshot",
                        new SaveArticleDraftRequest("OTHER", "其他", null, null, null),
                        owner),
                AnnotationErrorCodes.ARTICLE_NOT_IN_TASK);
        assertThat(draftRepository.count()).isZero();
    }

    private static TaskDocument task(
            TaskState state,
            FieldConfigSnapshot fieldConfigSnapshot) {
        Instant now = Instant.parse("2026-08-23T00:00:00Z");
        return new TaskDocument(
                "task-1",
                TaskType.ORDINARY,
                state,
                "law-1",
                "annotator-1",
                "标注员甲",
                "测试任务",
                null,
                "content-1",
                new TaskContentVersionSnapshot(
                        "content-1",
                        1,
                        List.of(new TaskArticleSnapshot(
                                "article-1", "第一条", "任务创建时正文", 0))),
                new TaskLawBaseInfoSnapshot(
                        "测试法",
                        "制定机关",
                        LocalDate.of(2026, 8, 23),
                        ValidityStatus.ACTIVE),
                List.of(),
                fieldConfigSnapshot,
                "admin-1",
                null,
                null,
                null,
                now,
                now);
    }

    private static FieldConfigSnapshot defaultSnapshot() {
        return new FieldConfigSnapshot(
                List.of(
                        new FieldConfigSnapshotItem("lawCategory", true),
                        new FieldConfigSnapshotItem("overallKeywords", true),
                        new FieldConfigSnapshotItem("summary", false),
                        new FieldConfigSnapshotItem("overallNote", false)),
                List.of(
                        new FieldConfigSnapshotItem("itemType", true),
                        new FieldConfigSnapshotItem("keywords", true),
                        new FieldConfigSnapshotItem("subjects", false),
                        new FieldConfigSnapshotItem("legalLiability", false),
                        new FieldConfigSnapshotItem("annotationNote", false)));
    }

    private static boolean required(
            List<FieldConfigSnapshotItem> fields,
            String fieldKey) {
        return fields.stream()
                .filter(field -> field.fieldKey().equals(fieldKey))
                .findFirst()
                .orElseThrow()
                .required();
    }

    private static UserPrincipal principal(String id, Role role) {
        Instant now = Instant.parse("2026-08-23T00:00:00Z");
        UserDocument user = new UserDocument(
                role == Role.ANNOTATOR ? "标注员甲" : "管理员",
                id,
                id,
                "$2a$12$hash",
                role,
                true,
                now,
                now);
        user.setId(id);
        return UserPrincipal.from(user);
    }

    private static void assertCode(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            String code) {
        assertThatThrownBy(action)
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(code);
    }
}
