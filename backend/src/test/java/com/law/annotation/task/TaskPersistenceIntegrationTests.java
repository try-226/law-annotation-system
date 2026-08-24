package com.law.annotation.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.field.FieldConfigDocument;
import com.law.annotation.field.FieldConfigRepository;
import com.law.annotation.field.FieldConfigService;
import com.law.annotation.field.FieldConfigSnapshot;
import com.law.annotation.field.FieldConfigSnapshotItem;
import com.law.annotation.law.ArticleSnapshot;
import com.law.annotation.law.LawAuditDocument;
import com.law.annotation.law.LawAuditRepository;
import com.law.annotation.law.LawDocument;
import com.law.annotation.law.LawErrorCodes;
import com.law.annotation.law.LawMaintenanceService;
import com.law.annotation.law.LawMutationGuard;
import com.law.annotation.law.LawOperationCoordinator;
import com.law.annotation.law.LawQueryService;
import com.law.annotation.law.LawRecycleService;
import com.law.annotation.law.LawRepository;
import com.law.annotation.law.LawStructureNode;
import com.law.annotation.law.LawStructureNodeType;
import com.law.annotation.law.dto.UpdateLawBaseRequest;
import com.law.annotation.task.dto.TaskDetailResponse;
import com.law.annotation.user.UserDocument;
import com.law.annotation.user.UserRepository;
import com.law.annotation.version.ContentVersionDocument;
import com.law.annotation.version.ContentVersionRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.bson.Document;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;

class TaskPersistenceIntegrationTests {

    private static MongoServer mongoServer;
    private static MongoClient mongoClient;
    private static MongoTemplate mongoTemplate;
    private static TaskRepository taskRepository;
    private static LawRepository lawRepository;
    private static LawAuditRepository lawAuditRepository;
    private static ContentVersionRepository contentVersionRepository;
    private static UserRepository userRepository;
    private static FieldConfigRepository fieldConfigRepository;
    private static FieldConfigService fieldConfigService;
    private static LawQueryService lawQueryService;
    private static LawOperationCoordinator operationCoordinator;
    private static TaskService taskService;

    @BeforeAll
    static void startMongo() throws Exception {
        mongoServer = new MongoServer(new MemoryBackend());
        mongoClient = MongoClients.create(mongoServer.bindAndGetConnectionString());
        mongoTemplate = new MongoTemplate(mongoClient, "task_core_test");
        MongoRepositoryFactory factory = new MongoRepositoryFactory(mongoTemplate);
        taskRepository = factory.getRepository(TaskRepository.class);
        lawRepository = factory.getRepository(LawRepository.class);
        lawAuditRepository = factory.getRepository(LawAuditRepository.class);
        contentVersionRepository = factory.getRepository(ContentVersionRepository.class);
        userRepository = factory.getRepository(UserRepository.class);
        fieldConfigRepository = factory.getRepository(FieldConfigRepository.class);
        new TaskIndexInitializer(mongoTemplate).run(new DefaultApplicationArguments());
        fieldConfigService = new FieldConfigService(fieldConfigRepository);
        lawQueryService = new LawQueryService(lawRepository, contentVersionRepository);
        operationCoordinator = new LawOperationCoordinator(mongoTemplate);
        taskService = new TaskService(
                taskRepository,
                lawRepository,
                contentVersionRepository,
                userRepository,
                fieldConfigService,
                mongoTemplate,
                operationCoordinator);
    }

    @AfterAll
    static void stopMongo() {
        mongoClient.close();
        mongoServer.shutdown();
    }

    @BeforeEach
    void clearData() {
        mongoTemplate.remove(new Query(), TaskDocument.class);
        mongoTemplate.remove(new Query(), LawDocument.class);
        mongoTemplate.remove(new Query(), ContentVersionDocument.class);
        mongoTemplate.remove(new Query(), LawAuditDocument.class);
        mongoTemplate.remove(new Query(), UserDocument.class);
        mongoTemplate.remove(new Query(), FieldConfigDocument.class);
    }

    @Test
    void concurrentCreationForSameLawAllowsExactlyOneActiveTask() throws Exception {
        insertEligibleLawAndAnnotator();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> createAfterBarrier(ready, start, "任务一"));
            Future<String> second = executor.submit(() -> createAfterBarrier(ready, start, "任务二"));
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder("SUCCESS", TaskErrorCodes.TASK_ALREADY_EXISTS);
            assertThat(taskRepository.count()).isEqualTo(1);
            assertThat(mongoTemplate.count(
                            Query.query(Criteria.where("lawId").is("law-1")
                                    .and("taskState").in(TaskStateRules.UNFINISHED_STATES)),
                            TaskDocument.class))
                    .isEqualTo(1);
            assertThat(taskRepository.existsByLawIdAndTaskStateIn(
                    "law-1", TaskStateRules.UNFINISHED_STATES)).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void taskCreationClaimBlocksConcurrentLawMutationUntilTaskIsStored() throws Exception {
        insertEligibleLawAndAnnotator();
        CountDownLatch taskHasClaim = new CountDownLatch(1);
        CountDownLatch releaseTask = new CountDownLatch(1);
        TaskService blockingTaskService = taskServiceBlockingOnFieldSnapshot(
                taskHasClaim,
                releaseTask);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<TaskDetailResponse> task = executor.submit(() -> blockingTaskService.createOrdinaryTask(
                    "law-1", "annotator-1", "并发快照任务", null, "admin-1"));
            assertThat(taskHasClaim.await(5, TimeUnit.SECONDS)).isTrue();

            assertCode(
                    () -> maintenanceService(List.of(new TaskLawMutationGuard(taskRepository)))
                            .updateBase(
                                    "law-1",
                                    new UpdateLawBaseRequest(
                                            "不应生效的新名称",
                                            "制定机关",
                                            LocalDate.of(2026, 8, 23),
                                            ValidityStatus.ACTIVE),
                                    "admin-1"),
                    LawErrorCodes.VERSION_CONFLICT);

            releaseTask.countDown();
            assertThat(task.get(5, TimeUnit.SECONDS).taskState())
                    .isEqualTo(TaskState.PENDING_ANNOTATION);
            assertThat(lawRepository.findById("law-1").orElseThrow().getName())
                    .isEqualTo("测试法");
            assertThat(taskRepository.count()).isEqualTo(1);
        } finally {
            releaseTask.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void taskCreationClaimPreventsConcurrentPhysicalLawDeletion() throws Exception {
        insertEligibleLawAndAnnotator();
        CountDownLatch taskHasClaim = new CountDownLatch(1);
        CountDownLatch releaseTask = new CountDownLatch(1);
        TaskService blockingTaskService = taskServiceBlockingOnFieldSnapshot(
                taskHasClaim,
                releaseTask);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<TaskDetailResponse> task = executor.submit(() -> blockingTaskService.createOrdinaryTask(
                    "law-1", "annotator-1", "删除竞争任务", null, "admin-1"));
            assertThat(taskHasClaim.await(5, TimeUnit.SECONDS)).isTrue();

            assertCode(
                    () -> recycleService(List.of(new TaskLawMutationGuard(taskRepository)))
                            .deleteLaw("law-1"),
                    LawErrorCodes.VERSION_CONFLICT);

            releaseTask.countDown();
            assertThat(task.get(5, TimeUnit.SECONDS).lawId()).isEqualTo("law-1");
            assertThat(lawRepository.findById("law-1")).isPresent();
            assertThat(contentVersionRepository.findById("content-1")).isPresent();
            assertThat(taskRepository.count()).isEqualTo(1);
        } finally {
            releaseTask.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void lawMutationClaimPreventsTaskFromUsingAnInFlightSnapshot() throws Exception {
        insertEligibleLawAndAnnotator();
        CountDownLatch lawHasClaim = new CountDownLatch(1);
        CountDownLatch releaseLaw = new CountDownLatch(1);
        LawMutationGuard blockingGuard = lawId -> {
            lawHasClaim.countDown();
            await(releaseLaw);
        };
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> mutation = executor.submit(() -> maintenanceService(List.of(blockingGuard))
                    .updateBase(
                            "law-1",
                            new UpdateLawBaseRequest(
                                    "修改后的测试法",
                                    "制定机关",
                                    LocalDate.of(2026, 8, 23),
                                    ValidityStatus.ACTIVE),
                            "admin-1"));
            assertThat(lawHasClaim.await(5, TimeUnit.SECONDS)).isTrue();

            assertCode(
                    () -> taskService.createOrdinaryTask(
                            "law-1", "annotator-1", "不得创建", null, "admin-1"),
                    TaskErrorCodes.TASK_ALREADY_EXISTS);

            releaseLaw.countDown();
            mutation.get(5, TimeUnit.SECONDS);
            assertThat(lawRepository.findById("law-1").orElseThrow().getName())
                    .isEqualTo("修改后的测试法");
            assertThat(taskRepository.count()).isZero();
        } finally {
            releaseLaw.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentStartTransitionsExactlyOnce() throws Exception {
        insertEligibleLawAndAnnotator();
        TaskDetailResponse created = taskService.createOrdinaryTask(
                "law-1", "annotator-1", "并发开始", null, "admin-1");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(
                    () -> startAfterBarrier(created.taskId(), ready, start));
            Future<String> second = executor.submit(
                    () -> startAfterBarrier(created.taskId(), ready, start));
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(
                            "SUCCESS",
                            TaskErrorCodes.INVALID_STATE_TRANSITION);
            assertThat(taskService.getDetail(created.taskId(), principal("annotator-1", Role.ANNOTATOR)).taskState())
                    .isEqualTo(TaskState.ANNOTATING);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void historicalTaskKeepsCreationSnapshotsAfterSourceChanges() {
        insertEligibleLawAndAnnotator();
        TaskDetailResponse created = taskService.createOrdinaryTask(
                "law-1", "annotator-1", "快照任务", null, "admin-1");
        boolean summaryRequiredAtCreation = created.fieldConfigSnapshot().overall().stream()
                .filter(item -> item.fieldKey().equals("summary"))
                .findFirst()
                .map(FieldConfigSnapshotItem::required)
                .orElseThrow();

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is("law-1")),
                new Update()
                        .set("name", "修改后的法律")
                        .set("structure", List.of()),
                LawDocument.class);
        fieldConfigService.updateRequired("summary", !summaryRequiredAtCreation, "admin-1", Role.ADMIN);

        TaskDetailResponse stored = taskService.getDetail(
                created.taskId(), principal("admin-1", Role.ADMIN));
        assertThat(stored.lawBaseInfoSnapshot().name()).isEqualTo("测试法");
        assertThat(stored.structureSnapshot()).hasSize(1);
        assertThat(stored.contentVersionSnapshot().articles())
                .extracting(TaskArticleSnapshot::body)
                .containsExactly("原始正文");
        assertThat(stored.fieldConfigSnapshot().overall().stream()
                        .filter(item -> item.fieldKey().equals("summary"))
                        .findFirst()
                        .orElseThrow()
                        .required())
                .isEqualTo(summaryRequiredAtCreation);
        assertThat(taskService.list(
                                "快照",
                                null,
                                "law-1",
                                "annotator-1",
                                null,
                                0,
                                20,
                                principal("admin-1", Role.ADMIN))
                        .items())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.taskId()).isEqualTo(created.taskId());
                    assertThat(item.lawName()).isEqualTo("测试法");
                    assertThat(item.annotatorName()).isEqualTo("标注员甲");
                });
    }

    @Test
    void businessHistoryIncludesAnnotatorCreatorAndCanceler() {
        mongoTemplate.getCollection("tasks").insertMany(List.of(
                historyTask("task-annotator", "law-annotator")
                        .append("annotatorId", "annotator-history"),
                historyTask("task-creator", "law-creator")
                        .append("createdBy", "creator-history"),
                historyTask("task-canceler", "law-canceler")
                        .append("canceledBy", "canceler-history")));
        TaskUserBusinessUsageAdapter adapter = new TaskUserBusinessUsageAdapter(taskRepository);

        assertThat(adapter.hasBusinessHistory("annotator-history")).isTrue();
        assertThat(adapter.hasBusinessHistory("creator-history")).isTrue();
        assertThat(adapter.hasBusinessHistory("canceler-history")).isTrue();
        assertThat(adapter.hasBusinessHistory("no-task-history")).isFalse();
    }

    @Test
    void startAndCancelAreAtomicAndCancelReleasesUnfinishedStateSlot() {
        insertEligibleLawAndAnnotator();
        TaskDetailResponse created = taskService.createOrdinaryTask(
                "law-1", "annotator-1", "状态任务", null, "admin-1");

        assertThat(taskService.start(created.taskId(), "annotator-1").taskState())
                .isEqualTo(TaskState.ANNOTATING);
        assertCode(
                () -> taskService.start(created.taskId(), "annotator-1"),
                TaskErrorCodes.INVALID_STATE_TRANSITION);

        TaskDetailResponse canceled = taskService.cancel(
                created.taskId(), "  业务调整  ", principal("admin-1", Role.ADMIN));
        assertThat(canceled.taskState()).isEqualTo(TaskState.CANCELED);
        assertThat(canceled.cancelReason()).isEqualTo("业务调整");
        assertThat(canceled.canceledBy()).isEqualTo("admin-1");
        assertThat(canceled.canceledAt()).isNotNull();
        assertThat(taskRepository.existsByLawIdAndTaskStateIn(
                "law-1", TaskStateRules.UNFINISHED_STATES)).isFalse();
    }

    @Test
    void canceledAndApprovedStatesDoNotBlockCreationPrecheck() {
        insertEligibleLawAndAnnotator();
        TaskDetailResponse first = taskService.createOrdinaryTask(
                "law-1", "annotator-1", "首个任务", null, "admin-1");
        taskService.cancel(first.taskId(), "取消", principal("admin-1", Role.ADMIN));
        assertThat(taskRepository.existsByLawIdAndTaskStateIn(
                "law-1", TaskStateRules.UNFINISHED_STATES)).isFalse();

        // mongo-java-server 1.47 ignores partial filters and creates a full lawId
        // unique index. Remove the ended fixture only to continue exercising Service
        // state predicates; the real Mongo partial-index behavior is verified separately.
        mongoTemplate.remove(new Query(), TaskDocument.class);
        TaskDetailResponse second = taskService.createOrdinaryTask(
                "law-1", "annotator-1", "批准后任务", null, "admin-1");
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(second.taskId())),
                new Update().set("taskState", TaskState.APPROVED),
                TaskDocument.class);
        assertThat(taskRepository.existsByLawIdAndTaskStateIn(
                "law-1", TaskStateRules.UNFINISHED_STATES)).isFalse();

        mongoTemplate.remove(new Query(), TaskDocument.class);
        assertThat(taskService.createOrdinaryTask(
                        "law-1", "annotator-1", "再次创建", null, "admin-1")
                        .taskState())
                .isEqualTo(TaskState.PENDING_ANNOTATION);
    }

    @Test
    void pendingTaskCanBeCanceledButPendingReviewCannot() {
        insertEligibleLawAndAnnotator();
        TaskDetailResponse pending = taskService.createOrdinaryTask(
                "law-1", "annotator-1", null, null, "admin-1");
        assertThat(taskService.cancel(
                        pending.taskId(), "取消", principal("admin-1", Role.ADMIN)).taskState())
                .isEqualTo(TaskState.CANCELED);
        assertThat(taskRepository.existsByLawIdAndTaskStateIn(
                "law-1", TaskStateRules.UNFINISHED_STATES)).isFalse();

        // mongo-java-server 1.47 does not implement partial index filters and treats
        // the production partial unique index as a full unique lawId index.
        mongoTemplate.remove(new Query(), TaskDocument.class);

        TaskDetailResponse next = taskService.createOrdinaryTask(
                "law-1", "annotator-1", null, null, "admin-1");
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(next.taskId())),
                new Update().set("taskState", TaskState.PENDING_REVIEW),
                TaskDocument.class);

        assertCode(
                () -> taskService.cancel(
                        next.taskId(), "不能取消", principal("admin-1", Role.ADMIN)),
                TaskErrorCodes.INVALID_STATE_TRANSITION);
    }

    private static TaskService taskServiceBlockingOnFieldSnapshot(
            CountDownLatch claimed,
            CountDownLatch release) {
        FieldConfigSnapshot snapshot = fieldConfigService.getCurrentSnapshot();
        FieldConfigService blockingFieldConfig = mock(FieldConfigService.class);
        when(blockingFieldConfig.getCurrentSnapshot()).thenAnswer(invocation -> {
            claimed.countDown();
            release.await();
            return snapshot;
        });
        return new TaskService(
                taskRepository,
                lawRepository,
                contentVersionRepository,
                userRepository,
                blockingFieldConfig,
                mongoTemplate,
                operationCoordinator);
    }

    private static LawMaintenanceService maintenanceService(List<LawMutationGuard> guards) {
        return new LawMaintenanceService(
                lawRepository,
                contentVersionRepository,
                lawAuditRepository,
                lawQueryService,
                mongoTemplate,
                guards,
                operationCoordinator);
    }

    private static LawRecycleService recycleService(List<LawMutationGuard> guards) {
        return new LawRecycleService(
                lawRepository,
                lawQueryService,
                mongoTemplate,
                guards,
                operationCoordinator);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("并发测试等待被中断", exception);
        }
    }

    private static String createAfterBarrier(
            CountDownLatch ready,
            CountDownLatch start,
            String taskName) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            taskService.createOrdinaryTask(
                    "law-1", "annotator-1", taskName, null, "admin-1");
            return "SUCCESS";
        } catch (ApiException exception) {
            return exception.getCode();
        }
    }

    private static String startAfterBarrier(
            String taskId,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            taskService.start(taskId, "annotator-1");
            return "SUCCESS";
        } catch (ApiException exception) {
            return exception.getCode();
        }
    }

    private static void insertEligibleLawAndAnnotator() {
        Instant now = Instant.parse("2026-08-23T00:00:00Z");
        ArticleSnapshot article = new ArticleSnapshot(
                "article-1", "第一条", "原始正文", 0);
        contentVersionRepository.insert(new ContentVersionDocument(
                "content-1", "law-1", 1, List.of(article), "admin-1", now));
        lawRepository.insert(LawDocument.createInitial(
                "law-1",
                "测试法",
                "制定机关",
                LocalDate.of(2026, 8, 23),
                ValidityStatus.ACTIVE,
                List.of(new LawStructureNode(
                        "chapter-1",
                        LawStructureNodeType.CHAPTER,
                        "第一章 总则",
                        null,
                        0,
                        List.of("article-1"))),
                "content-1",
                now));
        UserDocument annotator = new UserDocument(
                "标注员甲",
                "annotator1",
                "annotator1",
                "$2a$12$hash",
                Role.ANNOTATOR,
                true,
                now,
                now);
        annotator.setId("annotator-1");
        userRepository.insert(annotator);
    }

    private static UserPrincipal principal(String id, Role role) {
        Instant now = Instant.parse("2026-08-23T00:00:00Z");
        UserDocument user = new UserDocument(
                role == Role.ADMIN ? "管理员" : "标注员甲",
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

    private static Document historyTask(String taskId, String lawId) {
        return new Document("_id", taskId)
                .append("lawId", lawId)
                .append("taskType", "ORDINARY")
                .append("taskState", "CANCELED");
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
