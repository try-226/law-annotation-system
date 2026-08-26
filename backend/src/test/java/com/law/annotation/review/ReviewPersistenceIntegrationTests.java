package com.law.annotation.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.law.annotation.annotation.ArticleDraftValues;
import com.law.annotation.annotation.OverallDraftValues;
import com.law.annotation.annotation.RereviewSubmissionIndexInitializer;
import com.law.annotation.annotation.TaskDraftDocument;
import com.law.annotation.annotation.TaskSubmissionDocument;
import com.law.annotation.annotation.TaskSubmissionIndexInitializer;
import com.law.annotation.annotation.TaskSubmissionRepository;
import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.ItemType;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.field.FieldConfigSnapshot;
import com.law.annotation.law.LawDocument;
import com.law.annotation.task.TaskArticleSnapshot;
import com.law.annotation.task.TaskContentVersionSnapshot;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.task.TaskLawBaseInfoSnapshot;
import com.law.annotation.task.TaskRepository;
import com.law.annotation.user.UserDocument;
import com.law.annotation.version.AnnotationVersionDocument;
import com.law.annotation.version.AnnotationVersionIndexInitializer;
import com.law.annotation.version.AnnotationVersionRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.http.HttpStatus;

class ReviewPersistenceIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-08-26T00:00:00Z");

    private static MongoServer mongoServer;
    private static MongoClient mongoClient;
    private static MongoTemplate mongoTemplate;
    private static TaskRepository taskRepository;
    private static TaskSubmissionRepository submissionRepository;
    private static ReviewRoundRepository roundRepository;
    private static AnnotationVersionRepository annotationVersionRepository;
    private static ReviewService reviewService;

    @BeforeAll
    static void startMongo() throws Exception {
        mongoServer = new MongoServer(new MemoryBackend());
        mongoClient = MongoClients.create(mongoServer.bindAndGetConnectionString());
        mongoTemplate = new MongoTemplate(mongoClient, "review_core_test");
        MongoRepositoryFactory factory = new MongoRepositoryFactory(mongoTemplate);
        taskRepository = factory.getRepository(TaskRepository.class);
        submissionRepository = factory.getRepository(TaskSubmissionRepository.class);
        roundRepository = factory.getRepository(ReviewRoundRepository.class);
        annotationVersionRepository = factory.getRepository(AnnotationVersionRepository.class);
        new TaskSubmissionIndexInitializer(mongoTemplate)
                .run(new DefaultApplicationArguments());
        new RereviewSubmissionIndexInitializer(mongoTemplate)
                .run(new DefaultApplicationArguments());
        new ReviewRoundIndexInitializer(mongoTemplate)
                .run(new DefaultApplicationArguments());
        new AnnotationVersionIndexInitializer(mongoTemplate)
                .run(new DefaultApplicationArguments());
        reviewService = new ReviewService(
                taskRepository,
                submissionRepository,
                roundRepository,
                annotationVersionRepository,
                mongoTemplate);
    }

    @AfterAll
    static void stopMongo() {
        mongoClient.close();
        mongoServer.shutdown();
    }

    @BeforeEach
    void clearData() {
        mongoTemplate.remove(new Query(), TaskDocument.class);
        mongoTemplate.remove(new Query(), TaskSubmissionDocument.class);
        mongoTemplate.remove(new Query(), ReviewRoundDocument.class);
        mongoTemplate.remove(new Query(), AnnotationVersionDocument.class);
        mongoTemplate.remove(new Query(), LawDocument.class);
        taskRepository.insert(task(TaskState.PENDING_REVIEW, "submission-1"));
        submissionRepository.insert(submission(
                "submission-1", 1, null, List.of(), "初次冻结"));
        mongoTemplate.insert(law());
    }

    @Test
    void concurrentStartCreatesOneRoundAndAssignsExactlyOneReviewer() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(
                    () -> startAfterBarrier("admin-1", ready, start));
            Future<String> second = executor.submit(
                    () -> startAfterBarrier("admin-2", ready, start));
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .contains("SUCCESS")
                    .contains(ReviewErrorCodes.ALREADY_ASSIGNED);
            assertThat(roundRepository.count()).isEqualTo(1);
            ReviewRoundDocument round = roundRepository.findAll().getFirst();
            assertThat(round.getRequiredScope()).containsExactly(
                    ReviewItemLocator.overall(),
                    ReviewItemLocator.article("article-1"),
                    ReviewItemLocator.article("article-2"));
            assertThat(round.getUnreviewedCount()).isEqualTo(3);
            assertThat(round.getItemStates().values())
                    .allMatch(state -> state.name().equals("UNREVIEWED"));
            assertThat(taskRepository.findById("task-1").orElseThrow()
                    .getCurrentReviewRoundId()).isEqualTo(round.getReviewRoundId());

            String otherAdmin = round.getReviewerId().equals("admin-1")
                    ? "admin-2"
                    : "admin-1";
            assertThat(reviewService.getReview("task-1", admin(otherAdmin)).writable())
                    .isFalse();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void permissionsReasonValidationAndCompletenessAreEnforcedInService() {
        assertThatThrownBy(() -> reviewService.start("task-1", annotator()))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
        UserPrincipal reviewer = admin("admin-1");
        String roundId = reviewService.start("task-1", reviewer).reviewRoundId();

        assertThatThrownBy(() -> reviewService.check(
                        "task-1", roundId, ReviewItemLocator.overall(), admin("admin-2")))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ReviewErrorCodes.NOT_REVIEWER);
        assertThatThrownBy(() -> reviewService.complete(
                        "task-1", roundId, admin("admin-2")))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ReviewErrorCodes.NOT_REVIEWER);
        assertThatThrownBy(() -> reviewService.issue(
                        "task-1", roundId, ReviewItemLocator.overall(), "   ", reviewer))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException exception = (ApiException) error;
                    assertThat(exception.getStatus()).isEqualTo(
                            HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(exception.getCode()).isEqualTo(
                            ReviewErrorCodes.ISSUE_REASON_INVALID);
                });
        assertThatThrownBy(() -> reviewService.issue(
                        "task-1", roundId, ReviewItemLocator.overall(),
                        "问".repeat(501), reviewer))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        reviewService.issue(
                "task-1", roundId, ReviewItemLocator.overall(), "暂存问题", reviewer);
        var checkedAgain = reviewService.check(
                "task-1", roundId, ReviewItemLocator.overall(), reviewer);
        assertThat(checkedAgain.progress().needsChange()).isZero();
        assertThat(checkedAgain.items().getFirst().issue()).isNull();
        assertThatThrownBy(() -> reviewService.complete("task-1", roundId, reviewer))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException exception = (ApiException) error;
                    assertThat(exception.getStatus()).isEqualTo(
                            HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(exception.getCode()).isEqualTo(ReviewErrorCodes.INCOMPLETE);
                });
    }

    @Test
    void startAndCompleteRejectStaleTaskStateBeforeFormalSideEffects() {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is("task-1")),
                new Update().set("taskState", TaskState.ANNOTATING),
                TaskDocument.class);
        assertThatThrownBy(() -> reviewService.start("task-1", admin("admin-1")))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ReviewErrorCodes.INVALID_TASK_STATE);

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is("task-1")),
                new Update().set("taskState", TaskState.PENDING_REVIEW),
                TaskDocument.class);
        UserPrincipal reviewer = admin("admin-1");
        String roundId = reviewService.start("task-1", reviewer).reviewRoundId();
        reviewService.check("task-1", roundId, ReviewItemLocator.overall(), reviewer);
        reviewService.check(
                "task-1", roundId, ReviewItemLocator.article("article-1"), reviewer);
        reviewService.check(
                "task-1", roundId, ReviewItemLocator.article("article-2"), reviewer);
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is("task-1")),
                new Update().set("taskState", TaskState.PARTIALLY_REJECTED),
                TaskDocument.class);

        assertThatThrownBy(() -> reviewService.complete("task-1", roundId, reviewer))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ReviewErrorCodes.INVALID_TASK_STATE);
        assertThat(mongoTemplate.count(new Query(), AnnotationVersionDocument.class)).isZero();
        assertThat(roundRepository.findById(roundId).orElseThrow()
                .getCompletionStartedAt()).isNull();
        assertThat(mongoTemplate.findById("law-1", LawDocument.class)
                .getCurrentAnnotationVersionId()).isNull();
    }

    @Test
    void approvedCompletionRejectsChangedLawContentVersionBeforeFormalSideEffects() {
        UserPrincipal reviewer = admin("admin-1");
        String roundId = reviewService.start("task-1", reviewer).reviewRoundId();
        reviewService.check("task-1", roundId, ReviewItemLocator.overall(), reviewer);
        reviewService.check(
                "task-1", roundId, ReviewItemLocator.article("article-1"), reviewer);
        reviewService.check(
                "task-1", roundId, ReviewItemLocator.article("article-2"), reviewer);
        LawDocument before = mongoTemplate.findById("law-1", LawDocument.class);
        assertThat(before).isNotNull();
        String previousAnnotationVersionId = before.getCurrentAnnotationVersionId();
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is("law-1")),
                new Update().set("currentContentVersionId", "content-2"),
                LawDocument.class);

        assertThatThrownBy(() -> reviewService.complete("task-1", roundId, reviewer))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException exception = (ApiException) error;
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo(
                            ReviewErrorCodes.COMPLETION_CONFLICT);
                });

        assertThat(mongoTemplate.count(new Query(), AnnotationVersionDocument.class)).isZero();
        LawDocument currentLaw = mongoTemplate.findById("law-1", LawDocument.class);
        assertThat(currentLaw).isNotNull();
        assertThat(currentLaw.getCurrentAnnotationVersionId())
                .isEqualTo(previousAnnotationVersionId);
        assertThat(taskRepository.findById("task-1").orElseThrow().getTaskState())
                .isEqualTo(TaskState.PENDING_REVIEW);
        assertThat(roundRepository.findById(roundId).orElseThrow().getAnnotationVersionId())
                .isNull();
    }

    @Test
    void approvedCompletionUsesFrozenSubmissionAndDuplicateCreatesNoSecondVersion() {
        UserPrincipal reviewer = admin("admin-1");
        String roundId = reviewService.start("task-1", reviewer).reviewRoundId();
        reviewService.check("task-1", roundId, ReviewItemLocator.overall(), reviewer);
        reviewService.check(
                "task-1", roundId, ReviewItemLocator.article("article-1"), reviewer);
        reviewService.check(
                "task-1", roundId, ReviewItemLocator.article("article-2"), reviewer);

        mongoTemplate.insert(new TaskDraftDocument(
                "task-1",
                new OverallDraftValues("民事", "非冻结草稿内容", null, null),
                Map.of(),
                99,
                "annotator-1",
                NOW,
                NOW));
        var completed = reviewService.complete("task-1", roundId, reviewer);

        AnnotationVersionDocument version = annotationVersionRepository
                .findBySourceTaskId("task-1")
                .orElseThrow();
        assertThat(version.getSeq()).isEqualTo(1);
        assertThat(version.getContentVersionId()).isEqualTo("content-1");
        assertThat(version.getOverallResult().overallKeywords()).isEqualTo("初次冻结");
        assertThat(version.getArticleResults()).containsOnlyKeys("article-1", "article-2");
        assertThat(completed.outcome()).isEqualTo(ReviewRoundOutcome.APPROVED);
        assertThat(taskRepository.findById("task-1").orElseThrow().getTaskState())
                .isEqualTo(TaskState.APPROVED);
        LawDocument currentLaw = mongoTemplate.findById("law-1", LawDocument.class);
        assertThat(currentLaw.getCurrentAnnotationVersionId()).isEqualTo(version.getId());

        assertThatThrownBy(() -> reviewService.complete("task-1", roundId, reviewer))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ReviewErrorCodes.ALREADY_COMPLETED);
        assertThat(mongoTemplate.count(new Query(), AnnotationVersionDocument.class))
                .isEqualTo(1);
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is("task-1")),
                new Update().set("overallDraft.overallKeywords", "通过后继续修改草稿"),
                TaskDraftDocument.class);
        assertThat(annotationVersionRepository.findById(version.getId()).orElseThrow()
                .getOverallResult().overallKeywords()).isEqualTo("初次冻结");
    }

    @Test
    void completionIntentRecoversPartialAnnotationAndLawWritesWithoutNewVersion() {
        UserPrincipal reviewer = admin("admin-1");
        String roundId = reviewService.start("task-1", reviewer).reviewRoundId();
        reviewService.check("task-1", roundId, ReviewItemLocator.overall(), reviewer);
        reviewService.check(
                "task-1", roundId, ReviewItemLocator.article("article-1"), reviewer);
        reviewService.check(
                "task-1", roundId, ReviewItemLocator.article("article-2"), reviewer);
        Instant intentAt = Instant.parse("2026-08-26T00:10:00Z");
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(roundId)),
                new Update()
                        .set("completionStartedAt", intentAt)
                        .set("completionOutcome", ReviewRoundOutcome.APPROVED),
                ReviewRoundDocument.class);
        TaskSubmissionDocument frozen = submissionRepository
                .findById("submission-1").orElseThrow();
        AnnotationVersionDocument partialVersion = new AnnotationVersionDocument(
                "annotation-recovery-1",
                "law-1",
                1,
                "content-1",
                frozen.getOverallSnapshot(),
                frozen.getArticleSnapshots(),
                "task-1",
                "submission-1",
                "admin-1",
                intentAt);
        annotationVersionRepository.insert(partialVersion);
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is("law-1")),
                new Update().set("currentAnnotationVersionId", partialVersion.getId()),
                LawDocument.class);

        var recovered = reviewService.complete("task-1", roundId, reviewer);

        assertThat(recovered.annotationVersionId()).isEqualTo(partialVersion.getId());
        assertThat(recovered.completedAt()).isNotNull();
        TaskDocument task = taskRepository.findById("task-1").orElseThrow();
        assertThat(task.getTaskState()).isEqualTo(TaskState.APPROVED);
        assertThat(task.getApprovedAnnotationVersionId()).isEqualTo(partialVersion.getId());
        assertThat(mongoTemplate.count(new Query(), AnnotationVersionDocument.class))
                .isEqualTo(1);
    }

    @Test
    void concurrentDuplicateCompleteConvergesToOneFormalAnnotationVersion() throws Exception {
        UserPrincipal reviewer = admin("admin-1");
        String roundId = reviewService.start("task-1", reviewer).reviewRoundId();
        reviewService.check("task-1", roundId, ReviewItemLocator.overall(), reviewer);
        reviewService.check(
                "task-1", roundId, ReviewItemLocator.article("article-1"), reviewer);
        reviewService.check(
                "task-1", roundId, ReviewItemLocator.article("article-2"), reviewer);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(
                    () -> completeAfterBarrier(roundId, reviewer, ready, start));
            Future<String> second = executor.submit(
                    () -> completeAfterBarrier(roundId, reviewer, ready, start));
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .allMatch(result -> result.equals("SUCCESS")
                            || result.equals(ReviewErrorCodes.ALREADY_COMPLETED));
            assertThat(mongoTemplate.count(new Query(), AnnotationVersionDocument.class))
                    .isEqualTo(1);
            assertThat(taskRepository.findById("task-1").orElseThrow().getTaskState())
                    .isEqualTo(TaskState.APPROVED);
            assertThat(roundRepository.findById(roundId).orElseThrow().getCompletedAt())
                    .isNotNull();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void multipleRereviewsUseNewSubmissionsAndDoNotHitRoundUniqueness() {
        UserPrincipal initialReviewer = admin("admin-1");
        String firstRoundId = reviewService.start("task-1", initialReviewer).reviewRoundId();
        reviewService.check(
                "task-1", firstRoundId, ReviewItemLocator.overall(), initialReviewer);
        reviewService.issue(
                "task-1", firstRoundId, ReviewItemLocator.article("article-1"),
                "第一轮问题", initialReviewer);
        reviewService.check(
                "task-1", firstRoundId, ReviewItemLocator.article("article-2"),
                initialReviewer);
        reviewService.complete("task-1", firstRoundId, initialReviewer);
        assertThat(mongoTemplate.count(new Query(), AnnotationVersionDocument.class)).isZero();

        TaskSubmissionDocument second = submission(
                "submission-2", 2, firstRoundId,
                List.of(ReviewItemLocator.article("article-1")), "第二次冻结");
        submissionRepository.insert(second);
        moveToPendingRereview(second.getSubmissionId());
        UserPrincipal secondReviewer = admin("admin-2");
        String secondRoundId = reviewService.start("task-1", secondReviewer).reviewRoundId();
        ReviewRoundDocument secondRound = roundRepository.findById(secondRoundId).orElseThrow();
        assertThat(secondRound.getRoundNo()).isEqualTo(2);
        assertThat(secondRound.getReviewerId()).isEqualTo("admin-2");
        assertThat(secondRound.getRequiredScope())
                .containsExactly(ReviewItemLocator.article("article-1"));

        reviewService.issue(
                "task-1", secondRoundId, ReviewItemLocator.article("article-2"),
                "复审发现的新问题", secondReviewer);
        reviewService.check(
                "task-1", secondRoundId, ReviewItemLocator.article("article-1"),
                secondReviewer);
        reviewService.complete("task-1", secondRoundId, secondReviewer);

        TaskSubmissionDocument third = submission(
                "submission-3", 3, secondRoundId,
                List.of(ReviewItemLocator.article("article-2")), "第三次冻结");
        submissionRepository.insert(third);
        moveToPendingRereview(third.getSubmissionId());
        var thirdRound = reviewService.start("task-1", admin("admin-3"));

        assertThat(thirdRound.roundNo()).isEqualTo(3);
        assertThat(thirdRound.roundType()).isEqualTo(ReviewRoundType.REREVIEW);
        assertThat(thirdRound.before().submissionId()).isEqualTo("submission-2");
        assertThat(thirdRound.after().submissionId()).isEqualTo("submission-3");
        assertThat(roundRepository.count()).isEqualTo(3);
        assertThat(submissionRepository.count()).isEqualTo(3);
    }

    @Test
    void rereviewCanBeApprovedByDifferentAdminAndCreatesA1FromRereviewSubmission() {
        UserPrincipal firstReviewer = admin("admin-1");
        String firstRoundId = reviewService.start("task-1", firstReviewer).reviewRoundId();
        reviewService.check("task-1", firstRoundId, ReviewItemLocator.overall(), firstReviewer);
        reviewService.issue(
                "task-1", firstRoundId, ReviewItemLocator.article("article-1"),
                "需要修改", firstReviewer);
        reviewService.check(
                "task-1", firstRoundId, ReviewItemLocator.article("article-2"),
                firstReviewer);
        reviewService.complete("task-1", firstRoundId, firstReviewer);

        TaskSubmissionDocument second = submission(
                "submission-2", 2, firstRoundId,
                List.of(ReviewItemLocator.article("article-1")), "复审冻结");
        submissionRepository.insert(second);
        moveToPendingRereview(second.getSubmissionId());
        UserPrincipal secondReviewer = admin("admin-2");
        String secondRoundId = reviewService.start("task-1", secondReviewer).reviewRoundId();
        reviewService.check(
                "task-1", secondRoundId, ReviewItemLocator.article("article-1"),
                secondReviewer);

        var completed = reviewService.complete("task-1", secondRoundId, secondReviewer);

        assertThat(completed.outcome()).isEqualTo(ReviewRoundOutcome.APPROVED);
        AnnotationVersionDocument version = annotationVersionRepository
                .findBySourceTaskId("task-1").orElseThrow();
        assertThat(version.getSeq()).isEqualTo(1);
        assertThat(version.getSourceSubmissionId()).isEqualTo("submission-2");
        assertThat(version.getOverallResult().overallKeywords()).isEqualTo("复审冻结");
        assertThat(version.getApprovedBy()).isEqualTo("admin-2");
    }

    private static void moveToPendingRereview(String submissionId) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is("task-1")),
                new Update()
                        .set("taskState", TaskState.PENDING_REREVIEW)
                        .set("currentSubmissionId", submissionId)
                        .unset("currentReviewRoundId"),
                TaskDocument.class);
    }

    private static String startAfterBarrier(
            String adminId,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            reviewService.start("task-1", admin(adminId));
            return "SUCCESS";
        } catch (ApiException exception) {
            return exception.getCode();
        }
    }

    private static String completeAfterBarrier(
            String roundId,
            UserPrincipal reviewer,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            reviewService.complete("task-1", roundId, reviewer);
            return "SUCCESS";
        } catch (ApiException exception) {
            return exception.getCode();
        }
    }

    private static TaskSubmissionDocument submission(
            String id,
            int no,
            String sourceRoundId,
            List<ReviewItemLocator> modifiedScope,
            String keywords) {
        Map<String, ArticleDraftValues> articles = new LinkedHashMap<>();
        articles.put("article-1", new ArticleDraftValues(
                ItemType.DEFINITION, "定义", null, null, null));
        articles.put("article-2", new ArticleDraftValues(
                ItemType.RIGHTS_DUTIES, "权利", null, null, null));
        return new TaskSubmissionDocument(
                id,
                "task-1",
                no,
                no,
                new OverallDraftValues("民事", keywords, null, null),
                articles,
                sourceRoundId,
                modifiedScope,
                "annotator-1",
                NOW.plusSeconds(no));
    }

    private static TaskDocument task(TaskState state, String initialSubmissionId) {
        return new TaskDocument(
                "task-1",
                TaskType.ORDINARY,
                state,
                "law-1",
                "annotator-1",
                "标注员甲",
                "审核测试任务",
                null,
                "content-1",
                new TaskContentVersionSnapshot(
                        "content-1",
                        1,
                        List.of(
                                new TaskArticleSnapshot(
                                        "article-1", "第一条", "任务快照正文一", 0),
                                new TaskArticleSnapshot(
                                        "article-2", "第二条", "任务快照正文二", 1))),
                new TaskLawBaseInfoSnapshot(
                        "测试法",
                        "制定机关",
                        LocalDate.of(2026, 8, 26),
                        ValidityStatus.ACTIVE),
                List.of(),
                new FieldConfigSnapshot(List.of(), List.of()),
                "admin-creator",
                initialSubmissionId,
                null,
                null,
                null,
                NOW,
                NOW);
    }

    private static LawDocument law() {
        return LawDocument.createInitial(
                "law-1",
                "测试法",
                "制定机关",
                LocalDate.of(2026, 8, 26),
                ValidityStatus.ACTIVE,
                List.of(),
                "content-1",
                NOW);
    }

    private static UserPrincipal admin(String id) {
        UserDocument user = new UserDocument(
                "管理员",
                id,
                id,
                "$2a$12$hash",
                Role.ADMIN,
                true,
                NOW,
                NOW);
        user.setId(id);
        return UserPrincipal.from(user);
    }

    private static UserPrincipal annotator() {
        UserDocument user = new UserDocument(
                "标注员",
                "annotator-1",
                "annotator-1",
                "$2a$12$hash",
                Role.ANNOTATOR,
                true,
                NOW,
                NOW);
        user.setId("annotator-1");
        return UserPrincipal.from(user);
    }
}
