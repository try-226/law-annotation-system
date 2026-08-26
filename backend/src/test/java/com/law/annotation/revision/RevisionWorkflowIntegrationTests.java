package com.law.annotation.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.law.annotation.annotation.AnnotationDraftService;
import com.law.annotation.annotation.ArticleDraftValues;
import com.law.annotation.annotation.OverallDraftValues;
import com.law.annotation.annotation.RereviewSubmissionIndexInitializer;
import com.law.annotation.annotation.TaskDraftDocument;
import com.law.annotation.annotation.TaskDraftRepository;
import com.law.annotation.annotation.TaskSubmissionDocument;
import com.law.annotation.annotation.TaskSubmissionIndexInitializer;
import com.law.annotation.annotation.TaskSubmissionRepository;
import com.law.annotation.annotation.dto.SaveArticleDraftRequest;
import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.ItemType;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.field.FieldConfigDocument;
import com.law.annotation.field.FieldConfigRepository;
import com.law.annotation.field.FieldConfigService;
import com.law.annotation.law.ArticleSnapshot;
import com.law.annotation.law.LawDocument;
import com.law.annotation.law.LawOperationCoordinator;
import com.law.annotation.law.LawRepository;
import com.law.annotation.law.PendingChangeSet;
import com.law.annotation.revision.dto.CreateRevisionTaskRequest;
import com.law.annotation.review.ReviewItemLocator;
import com.law.annotation.review.ReviewRoundDocument;
import com.law.annotation.review.ReviewRoundIndexInitializer;
import com.law.annotation.review.ReviewRoundOutcome;
import com.law.annotation.review.ReviewRoundRepository;
import com.law.annotation.review.ReviewService;
import com.law.annotation.review.dto.ReviewDetailResponse;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.task.TaskIndexInitializer;
import com.law.annotation.task.TaskRepository;
import com.law.annotation.task.TaskService;
import com.law.annotation.user.UserDocument;
import com.law.annotation.user.UserRepository;
import com.law.annotation.version.AnnotationVersionDocument;
import com.law.annotation.version.AnnotationVersionIndexInitializer;
import com.law.annotation.version.AnnotationVersionRepository;
import com.law.annotation.version.ContentVersionDocument;
import com.law.annotation.version.ContentVersionRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

class RevisionWorkflowIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-08-26T00:00:00Z");

    private static MongoServer mongoServer;
    private static MongoClient mongoClient;
    private static MongoTemplate mongoTemplate;
    private static TaskRepository taskRepository;
    private static LawRepository lawRepository;
    private static ContentVersionRepository contentVersionRepository;
    private static AnnotationVersionRepository annotationVersionRepository;
    private static UserRepository userRepository;
    private static FieldConfigRepository fieldConfigRepository;
    private static TaskDraftRepository draftRepository;
    private static TaskSubmissionRepository submissionRepository;
    private static ReviewRoundRepository reviewRoundRepository;
    private static FieldConfigService fieldConfigService;
    private static TaskService taskService;
    private static RevisionService revisionService;
    private static ReviewService reviewService;
    private static AnnotationDraftService draftService;

    @BeforeAll
    static void startMongo() throws Exception {
        mongoServer = new MongoServer(new MemoryBackend());
        mongoClient = MongoClients.create(mongoServer.bindAndGetConnectionString());
        mongoTemplate = new MongoTemplate(mongoClient, "revision_workflow_test");
        MongoRepositoryFactory factory = new MongoRepositoryFactory(mongoTemplate);
        taskRepository = factory.getRepository(TaskRepository.class);
        lawRepository = factory.getRepository(LawRepository.class);
        contentVersionRepository = factory.getRepository(ContentVersionRepository.class);
        annotationVersionRepository = factory.getRepository(AnnotationVersionRepository.class);
        userRepository = factory.getRepository(UserRepository.class);
        fieldConfigRepository = factory.getRepository(FieldConfigRepository.class);
        draftRepository = factory.getRepository(TaskDraftRepository.class);
        submissionRepository = factory.getRepository(TaskSubmissionRepository.class);
        reviewRoundRepository = factory.getRepository(ReviewRoundRepository.class);
        new TaskIndexInitializer(mongoTemplate).run(new DefaultApplicationArguments());
        new TaskSubmissionIndexInitializer(mongoTemplate)
                .run(new DefaultApplicationArguments());
        new RereviewSubmissionIndexInitializer(mongoTemplate)
                .run(new DefaultApplicationArguments());
        new ReviewRoundIndexInitializer(mongoTemplate)
                .run(new DefaultApplicationArguments());
        new AnnotationVersionIndexInitializer(mongoTemplate)
                .run(new DefaultApplicationArguments());
        fieldConfigService = new FieldConfigService(fieldConfigRepository);
        taskService = new TaskService(
                taskRepository,
                lawRepository,
                contentVersionRepository,
                userRepository,
                fieldConfigService,
                mongoTemplate,
                new LawOperationCoordinator(mongoTemplate));
        revisionService = new RevisionService(
                lawRepository,
                contentVersionRepository,
                annotationVersionRepository,
                taskService);
        reviewService = new ReviewService(
                taskRepository,
                submissionRepository,
                reviewRoundRepository,
                annotationVersionRepository,
                mongoTemplate);
        draftService = new AnnotationDraftService(
                taskRepository,
                draftRepository,
                submissionRepository,
                taskService,
                mongoTemplate,
                reviewService,
                annotationVersionRepository);
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
        mongoTemplate.remove(new Query(), AnnotationVersionDocument.class);
        mongoTemplate.remove(new Query(), UserDocument.class);
        mongoTemplate.remove(new Query(), FieldConfigDocument.class);
        mongoTemplate.remove(new Query(), TaskDraftDocument.class);
        mongoTemplate.remove(new Query(), TaskSubmissionDocument.class);
        mongoTemplate.remove(new Query(), ReviewRoundDocument.class);
        fieldConfigService.initializeDefaults();
        userRepository.insert(user("admin-1", Role.ADMIN, true));
        userRepository.insert(user("admin-2", Role.ADMIN, true));
        userRepository.insert(user("annotator-1", Role.ANNOTATOR, true));
    }

    @Test
    void contentChangeRevisionCreatesFullSubmissionAndCompleteA2() {
        Scenario scenario = insertContentChangeScenario();
        long contentCount = mongoTemplate.count(new Query(), ContentVersionDocument.class);

        TaskDocument task = createdTask(false, List.of());

        assertThat(task.getRevisionScope().mode()).isEqualTo(RevisionMode.CONTENT_CHANGE);
        assertThat(task.getRevisionScope().articleIds())
                .containsExactly("article-modified", "article-added");
        assertThat(task.getContentVersionId()).isEqualTo("content-2");
        assertThat(task.getBaseAnnotationVersionId()).isEqualTo("annotation-1");
        assertThat(task.getContentVersionSnapshot().contentVersionId()).isEqualTo("content-2");
        assertThat(task.getContentVersionSnapshot().articles())
                .extracting(article -> article.articleId())
                .containsExactly("article-modified", "article-added", "article-unchanged");
        assertThat(task.getLawBaseInfoSnapshot().name()).isEqualTo("测试法");
        assertThat(task.getStructureSnapshot()).isEmpty();
        assertThat(task.getFieldConfigSnapshot().overall()).isNotEmpty();
        assertThat(task.getFieldConfigSnapshot().article()).isNotEmpty();
        assertThat(task.getAnnotatorNameSnapshot()).isEqualTo("annotator-1");
        assertThat(mongoTemplate.count(new Query(), ContentVersionDocument.class))
                .isEqualTo(contentCount);

        taskService.start(task.getTaskId(), "annotator-1");
        draftService.saveArticle(
                task.getTaskId(), "article-modified", articleRequest("修改后", null),
                principal("annotator-1", Role.ANNOTATOR));
        draftService.saveArticle(
                task.getTaskId(), "article-added", articleRequest("新增", null),
                principal("annotator-1", Role.ANNOTATOR));
        assertCode(
                () -> draftService.saveArticle(
                        task.getTaskId(), "article-unchanged", articleRequest("越界", null),
                        principal("annotator-1", Role.ANNOTATOR)),
                RevisionErrorCodes.WRITE_OUTSIDE_SCOPE);

        draftService.submitReview(task.getTaskId(), principal("annotator-1", Role.ANNOTATOR));
        TaskSubmissionDocument submission = submissionRepository
                .findByTaskIdAndSubmissionNo(task.getTaskId(), 1)
                .orElseThrow();
        assertThat(submission.getArticleSnapshots().keySet())
                .containsExactlyInAnyOrder(
                        "article-modified", "article-added", "article-unchanged")
                .doesNotContain("article-deleted");
        assertThat(submission.getArticleSnapshots().get("article-unchanged"))
                .isEqualTo(scenario.baseResults().get("article-unchanged"));

        UserPrincipal reviewer = principal("admin-1", Role.ADMIN);
        ReviewDetailResponse review = reviewService.start(task.getTaskId(), reviewer);
        assertThat(review.items()).extracting(item -> item.locator().articleId())
                .containsExactly("article-modified", "article-added");
        assertThat(review.before().submissionId()).isEqualTo("annotation-1");
        assertThat(review.after().submissionId()).isEqualTo(submission.getSubmissionId());
        reviewService.check(
                task.getTaskId(), review.reviewRoundId(),
                ReviewItemLocator.article("article-modified"), reviewer);
        reviewService.check(
                task.getTaskId(), review.reviewRoundId(),
                ReviewItemLocator.article("article-added"), reviewer);
        reviewService.complete(task.getTaskId(), review.reviewRoundId(), reviewer);

        TaskDocument approved = taskRepository.findById(task.getTaskId()).orElseThrow();
        AnnotationVersionDocument a2 = annotationVersionRepository
                .findBySourceTaskId(task.getTaskId()).orElseThrow();
        LawDocument completedLaw = lawRepository.findById("law-1").orElseThrow();
        assertThat(approved.getTaskState()).isEqualTo(TaskState.APPROVED);
        assertThat(a2.getSeq()).isEqualTo(2);
        assertThat(a2.getContentVersionId()).isEqualTo("content-2");
        assertThat(a2.getArticleResults()).isEqualTo(submission.getArticleSnapshots());
        assertThat(completedLaw.getCurrentAnnotationVersionId()).isEqualTo(a2.getId());
        assertThat(completedLaw.isPendingRevision()).isFalse();
        assertThat(completedLaw.getPendingChangeSet().isEmpty()).isTrue();
    }

    @Test
    void requiredExemptionAllowsInheritedOutOfScopeEmptyValue() {
        insertAnnotationOnlyScenario();
        fieldConfigService.updateRequired("subjects", true, "admin-1", Role.ADMIN);
        TaskDocument task = createdTask(false, List.of("article-1"));
        taskService.start(task.getTaskId(), "annotator-1");
        draftService.saveArticle(
                task.getTaskId(), "article-1", articleRequest("修正", "主体一"),
                principal("annotator-1", Role.ANNOTATOR));

        draftService.submitReview(task.getTaskId(), principal("annotator-1", Role.ANNOTATOR));
        UserPrincipal reviewer = principal("admin-1", Role.ADMIN);
        ReviewDetailResponse review = reviewService.start(task.getTaskId(), reviewer);
        reviewService.check(
                task.getTaskId(), review.reviewRoundId(),
                ReviewItemLocator.article("article-1"), reviewer);
        reviewService.complete(task.getTaskId(), review.reviewRoundId(), reviewer);

        AnnotationVersionDocument a2 = annotationVersionRepository
                .findBySourceTaskId(task.getTaskId()).orElseThrow();
        assertThat(a2.getArticleResults().get("article-2").subjects()).isNull();
        assertThat(a2.getArticleResults().get("article-1").subjects()).isEqualTo("主体一");
    }

    @Test
    void revisionPartialRejectAndRereviewUseFullSnapshotsAndAllowNewReviewer() {
        insertContentChangeScenario();
        TaskDocument task = createdTask(false, List.of());
        UserPrincipal annotator = principal("annotator-1", Role.ANNOTATOR);
        taskService.start(task.getTaskId(), "annotator-1");
        draftService.saveArticle(
                task.getTaskId(), "article-modified", articleRequest("首次修改", null), annotator);
        draftService.saveArticle(
                task.getTaskId(), "article-added", articleRequest("首次新增", null), annotator);
        draftService.submitReview(task.getTaskId(), annotator);

        UserPrincipal firstReviewer = principal("admin-1", Role.ADMIN);
        ReviewDetailResponse firstRound = reviewService.start(task.getTaskId(), firstReviewer);
        reviewService.issue(
                task.getTaskId(), firstRound.reviewRoundId(),
                ReviewItemLocator.article("article-modified"), "需要修改", firstReviewer);
        reviewService.check(
                task.getTaskId(), firstRound.reviewRoundId(),
                ReviewItemLocator.article("article-added"), firstReviewer);
        reviewService.complete(task.getTaskId(), firstRound.reviewRoundId(), firstReviewer);
        assertThat(taskRepository.findById(task.getTaskId()).orElseThrow().getTaskState())
                .isEqualTo(TaskState.PARTIALLY_REJECTED);

        draftService.saveArticle(
                task.getTaskId(), "article-modified", articleRequest("复审修改", null), annotator);
        draftService.submitRereview(task.getTaskId(), annotator);
        TaskSubmissionDocument rereviewSubmission = submissionRepository
                .findTopByTaskIdOrderBySubmissionNoDesc(task.getTaskId()).orElseThrow();
        assertThat(rereviewSubmission.getSubmissionNo()).isEqualTo(2);
        assertThat(rereviewSubmission.getArticleSnapshots().keySet())
                .containsExactlyInAnyOrder(
                        "article-modified", "article-added", "article-unchanged");
        assertThat(rereviewSubmission.getArticleSnapshots().get("article-added").keywords())
                .isEqualTo("首次新增");

        UserPrincipal secondReviewer = principal("admin-2", Role.ADMIN);
        ReviewDetailResponse secondRound = reviewService.start(task.getTaskId(), secondReviewer);
        assertThat(secondRound.items()).singleElement().satisfies(item ->
                assertThat(item.locator().articleId()).isEqualTo("article-modified"));
        reviewService.check(
                task.getTaskId(), secondRound.reviewRoundId(),
                ReviewItemLocator.article("article-modified"), secondReviewer);
        reviewService.complete(task.getTaskId(), secondRound.reviewRoundId(), secondReviewer);
        AnnotationVersionDocument a2 = annotationVersionRepository
                .findBySourceTaskId(task.getTaskId()).orElseThrow();
        assertThat(a2.getApprovedBy()).isEqualTo("admin-2");
        assertThat(a2.getArticleResults().get("article-modified").keywords())
                .isEqualTo("复审修改");
    }

    @Test
    void duplicateAndConcurrentRevisionSubmitKeepOneFrozenSubmission() throws Exception {
        insertAnnotationOnlyScenario();
        TaskDocument task = createdTask(false, List.of("article-1"));
        UserPrincipal annotator = principal("annotator-1", Role.ANNOTATOR);
        taskService.start(task.getTaskId(), "annotator-1");
        draftService.saveArticle(
                task.getTaskId(), "article-1", articleRequest("并发", null), annotator);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> submitConcurrently(
                    task.getTaskId(), annotator, ready, start));
            Future<Boolean> second = executor.submit(() -> submitConcurrently(
                    task.getTaskId(), annotator, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(mongoTemplate.count(
                Query.query(org.springframework.data.mongodb.core.query.Criteria
                        .where("taskId").is(task.getTaskId())),
                TaskSubmissionDocument.class)).isEqualTo(1);
        assertThat(taskRepository.findById(task.getTaskId()).orElseThrow().getTaskState())
                .isEqualTo(TaskState.PENDING_REVIEW);
        assertCode(
                () -> draftService.submitReview(task.getTaskId(), annotator),
                "TASK.ALREADY_SUBMITTED");
    }

    @Test
    void concurrentRevisionCompletionCreatesOnlyOneAnnotationVersion() throws Exception {
        insertAnnotationOnlyScenario();
        TaskDocument task = createdTask(false, List.of("article-1"));
        UserPrincipal annotator = principal("annotator-1", Role.ANNOTATOR);
        UserPrincipal reviewer = principal("admin-1", Role.ADMIN);
        taskService.start(task.getTaskId(), annotator.id());
        draftService.saveArticle(
                task.getTaskId(), "article-1", articleRequest("并发完成", null), annotator);
        draftService.submitReview(task.getTaskId(), annotator);
        ReviewDetailResponse review = reviewService.start(task.getTaskId(), reviewer);
        reviewService.check(
                task.getTaskId(), review.reviewRoundId(),
                ReviewItemLocator.article("article-1"), reviewer);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> completeConcurrently(
                    task.getTaskId(), review.reviewRoundId(), reviewer, ready, start));
            Future<Boolean> second = executor.submit(() -> completeConcurrently(
                    task.getTaskId(), review.reviewRoundId(), reviewer, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(), second.get())).contains(true);
        }

        assertThat(annotationVersionRepository.findBySourceTaskId(task.getTaskId()))
                .isPresent();
        assertThat(mongoTemplate.count(
                Query.query(Criteria.where("sourceTaskId").is(task.getTaskId())),
                AnnotationVersionDocument.class)).isEqualTo(1);
        assertThat(taskRepository.findById(task.getTaskId()).orElseThrow().getTaskState())
                .isEqualTo(TaskState.APPROVED);
        assertThat(lawRepository.findById("law-1").orElseThrow().isPendingRevision())
                .isFalse();
    }

    @Test
    void revisionCompletionResumesFromPersistedIntentAndAnnotationVersion() {
        insertAnnotationOnlyScenario();
        TaskDocument task = createdTask(false, List.of("article-1"));
        UserPrincipal annotator = principal("annotator-1", Role.ANNOTATOR);
        UserPrincipal reviewer = principal("admin-1", Role.ADMIN);
        taskService.start(task.getTaskId(), annotator.id());
        draftService.saveArticle(
                task.getTaskId(), "article-1", articleRequest("恢复完成", null), annotator);
        draftService.submitReview(task.getTaskId(), annotator);
        TaskSubmissionDocument submission = submissionRepository
                .findByTaskIdAndSubmissionNo(task.getTaskId(), 1)
                .orElseThrow();
        ReviewDetailResponse review = reviewService.start(task.getTaskId(), reviewer);
        reviewService.check(
                task.getTaskId(), review.reviewRoundId(),
                ReviewItemLocator.article("article-1"), reviewer);

        Instant completionStartedAt = NOW.plusSeconds(300);
        AnnotationVersionDocument recoveredVersion = annotationVersionRepository.insert(
                new AnnotationVersionDocument(
                        "annotation-recovered",
                        task.getLawId(),
                        2,
                        task.getContentVersionId(),
                        submission.getOverallSnapshot(),
                        submission.getArticleSnapshots(),
                        task.getTaskId(),
                        submission.getSubmissionId(),
                        reviewer.id(),
                        completionStartedAt));
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(review.reviewRoundId())),
                new Update()
                        .set("completionStartedAt", completionStartedAt)
                        .set("completionOutcome", ReviewRoundOutcome.APPROVED)
                        .set("annotationVersionId", recoveredVersion.getId()),
                ReviewRoundDocument.class);
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(task.getLawId())),
                new Update()
                        .set("currentAnnotationVersionId", recoveredVersion.getId())
                        .set("updatedAt", completionStartedAt),
                LawDocument.class);

        reviewService.complete(task.getTaskId(), review.reviewRoundId(), reviewer);

        ReviewRoundDocument completedRound = reviewRoundRepository
                .findById(review.reviewRoundId()).orElseThrow();
        TaskDocument completedTask = taskRepository.findById(task.getTaskId()).orElseThrow();
        assertThat(completedRound.getCompletedAt()).isNotNull();
        assertThat(completedRound.getAnnotationVersionId()).isEqualTo(recoveredVersion.getId());
        assertThat(completedTask.getTaskState()).isEqualTo(TaskState.APPROVED);
        assertThat(completedTask.getApprovedAnnotationVersionId())
                .isEqualTo(recoveredVersion.getId());
        assertThat(mongoTemplate.count(
                Query.query(Criteria.where("sourceTaskId").is(task.getTaskId())),
                AnnotationVersionDocument.class)).isEqualTo(1);
    }

    @Test
    void contentChangeCompletionRecoversAfterLawPointerAndPendingStateAreFinalized() {
        insertModifiedOnlyScenario();
        AnnotationVersionDocument a1Before = annotationVersionRepository
                .findById("annotation-1").orElseThrow();
        ContentVersionDocument c1Before = contentVersionRepository
                .findById("content-1").orElseThrow();
        ContentVersionDocument c2Before = contentVersionRepository
                .findById("content-2").orElseThrow();
        long contentVersionCountBefore = mongoTemplate.count(
                new Query(), ContentVersionDocument.class);

        TaskDocument task = createdTask(false, List.of());
        UserPrincipal annotator = principal("annotator-1", Role.ANNOTATOR);
        UserPrincipal reviewer = principal("admin-1", Role.ADMIN);
        taskService.start(task.getTaskId(), annotator.id());
        draftService.saveArticle(
                task.getTaskId(), "article-1", articleRequest("正文修订标注", null), annotator);
        draftService.submitReview(task.getTaskId(), annotator);
        TaskSubmissionDocument submission = submissionRepository
                .findByTaskIdAndSubmissionNo(task.getTaskId(), 1)
                .orElseThrow();
        ReviewDetailResponse review = reviewService.start(task.getTaskId(), reviewer);
        reviewService.check(
                task.getTaskId(), review.reviewRoundId(),
                ReviewItemLocator.article("article-1"), reviewer);

        Instant completionStartedAt = NOW.plusSeconds(600);
        AnnotationVersionDocument a2 = annotationVersionRepository.insert(
                new AnnotationVersionDocument(
                        "annotation-content-recovered",
                        task.getLawId(),
                        2,
                        task.getContentVersionId(),
                        submission.getOverallSnapshot(),
                        submission.getArticleSnapshots(),
                        task.getTaskId(),
                        submission.getSubmissionId(),
                        reviewer.id(),
                        completionStartedAt));
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(review.reviewRoundId())),
                new Update()
                        .set("completionStartedAt", completionStartedAt)
                        .set("completionOutcome", ReviewRoundOutcome.APPROVED)
                        .set("annotationVersionId", a2.getId()),
                ReviewRoundDocument.class);
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(task.getLawId())),
                new Update()
                        .set("currentAnnotationVersionId", a2.getId())
                        .set("currentContentVersionId", task.getContentVersionId())
                        .set("pendingRevision", false)
                        .set("pendingChangeSet", PendingChangeSet.empty())
                        .set("updatedAt", completionStartedAt),
                LawDocument.class);

        assertThat(taskRepository.findById(task.getTaskId()).orElseThrow().getTaskState())
                .isEqualTo(TaskState.PENDING_REVIEW);
        assertThat(reviewRoundRepository.findById(review.reviewRoundId())
                .orElseThrow().getCompletedAt()).isNull();

        reviewService.complete(task.getTaskId(), review.reviewRoundId(), reviewer);

        AnnotationVersionDocument recoveredA2 = annotationVersionRepository
                .findBySourceTaskId(task.getTaskId()).orElseThrow();
        AnnotationVersionDocument a1After = annotationVersionRepository
                .findById("annotation-1").orElseThrow();
        LawDocument completedLaw = lawRepository.findById(task.getLawId()).orElseThrow();
        TaskDocument completedTask = taskRepository.findById(task.getTaskId()).orElseThrow();
        ReviewRoundDocument completedRound = reviewRoundRepository
                .findById(review.reviewRoundId()).orElseThrow();

        assertThat(mongoTemplate.count(new Query(), AnnotationVersionDocument.class))
                .isEqualTo(2);
        assertThat(mongoTemplate.count(
                Query.query(Criteria.where("sourceTaskId").is(task.getTaskId())),
                AnnotationVersionDocument.class)).isEqualTo(1);
        assertThat(recoveredA2.getId()).isEqualTo(a2.getId());
        assertThat(recoveredA2.getSeq()).isEqualTo(2);
        assertThat(recoveredA2.getContentVersionId()).isEqualTo("content-2");
        assertThat(completedLaw.getCurrentContentVersionId()).isEqualTo("content-2");
        assertThat(completedLaw.getCurrentAnnotationVersionId()).isEqualTo(a2.getId());
        assertThat(completedLaw.isPendingRevision()).isFalse();
        assertThat(completedLaw.getPendingChangeSet().isEmpty()).isTrue();
        assertThat(completedTask.getTaskState()).isEqualTo(TaskState.APPROVED);
        assertThat(completedTask.getApprovedAnnotationVersionId()).isEqualTo(a2.getId());
        assertThat(completedRound.getCompletedAt()).isNotNull();
        assertThat(completedRound.getAnnotationVersionId()).isEqualTo(a2.getId());
        assertThat(a1After).usingRecursiveComparison().isEqualTo(a1Before);
        assertThat(mongoTemplate.count(new Query(), ContentVersionDocument.class))
                .isEqualTo(contentVersionCountBefore);
        assertThat(contentVersionRepository.findById("content-1").orElseThrow())
                .usingRecursiveComparison().isEqualTo(c1Before);
        assertThat(contentVersionRepository.findById("content-2").orElseThrow())
                .usingRecursiveComparison().isEqualTo(c2Before);
    }

    @Test
    void deletionOnlyEmptyScopeCompletesAndExcludesDeletedArticle() {
        insertDeletionOnlyScenario();
        TaskDocument task = createdTask(false, List.of());
        assertThat(task.getRevisionScope().articleIds()).isEmpty();

        taskService.start(task.getTaskId(), "annotator-1");
        draftService.submitReview(task.getTaskId(), principal("annotator-1", Role.ANNOTATOR));
        TaskSubmissionDocument submission = submissionRepository
                .findByTaskIdAndSubmissionNo(task.getTaskId(), 1).orElseThrow();
        assertThat(submission.getArticleSnapshots().keySet())
                .containsExactly("article-kept")
                .doesNotContain("article-deleted");

        UserPrincipal reviewer = principal("admin-1", Role.ADMIN);
        ReviewDetailResponse review = reviewService.start(task.getTaskId(), reviewer);
        assertThat(review.items()).isEmpty();
        reviewService.complete(task.getTaskId(), review.reviewRoundId(), reviewer);

        AnnotationVersionDocument a2 = annotationVersionRepository
                .findBySourceTaskId(task.getTaskId()).orElseThrow();
        LawDocument law = lawRepository.findById("law-1").orElseThrow();
        assertThat(a2.getArticleResults().keySet()).containsExactly("article-kept");
        assertThat(a2.getContentVersionId()).isEqualTo("content-2");
        assertThat(law.isPendingRevision()).isFalse();
        assertThat(law.getPendingChangeSet().isEmpty()).isTrue();
    }

    @Test
    void concurrentRevisionCreationAllowsExactlyOneActiveTask() throws Exception {
        insertAnnotationOnlyScenario();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> createConcurrently(ready, start));
            Future<Boolean> second = executor.submit(() -> createConcurrently(ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        }
        assertThat(taskRepository.findAll()).hasSize(1);
    }

    @Test
    void cancelKeepsLawRevisionBasisForBothModes() {
        insertContentChangeScenario();
        TaskDocument contentTask = createdTask(false, List.of());
        taskService.cancel(contentTask.getTaskId(), "暂不修订", principal("admin-1", Role.ADMIN));
        LawDocument contentLaw = lawRepository.findById("law-1").orElseThrow();
        assertThat(contentLaw.isPendingRevision()).isTrue();
        assertThat(contentLaw.getPendingChangeSet().isEmpty()).isFalse();
        assertThat(contentLaw.getCurrentAnnotationVersionId()).isEqualTo("annotation-1");

        clearData();
        insertAnnotationOnlyScenario();
        TaskDocument annotationTask = createdTask(true, List.of());
        taskService.cancel(
                annotationTask.getTaskId(), "取消修正", principal("admin-1", Role.ADMIN));
        LawDocument annotationLaw = lawRepository.findById("law-1").orElseThrow();
        assertThat(annotationLaw.isPendingRevision()).isFalse();
        assertThat(annotationLaw.getCurrentAnnotationVersionId()).isEqualTo("annotation-1");
    }

    @Test
    void revisionEligibilityRejectsDeletedLawMissingAAndDisabledAnnotator() {
        insertAnnotationOnlyScenario();
        UserDocument disabled = userRepository.findById("annotator-1").orElseThrow();
        disabled.setEnabled(false);
        userRepository.save(disabled);
        assertCode(
                () -> createRevision(false, List.of("article-1")),
                "TASK.ANNOTATOR_DISABLED");

        clearData();
        insertAnnotationOnlyScenario();
        LawDocument deleted = lawRepository.findById("law-1").orElseThrow();
        deleted.markDeleted(NOW.plusSeconds(10));
        lawRepository.save(deleted);
        assertCode(
                () -> createRevision(false, List.of("article-1")),
                "TASK.LAW_DELETED");

        clearData();
        ContentVersionDocument c1 = content(
                "content-1", 1, List.of(article("article-1", "第一条", "正文", 0)));
        contentVersionRepository.insert(c1);
        lawRepository.insert(LawDocument.createInitial(
                "law-1", "测试法", "制定机关", LocalDate.of(2026, 8, 26),
                ValidityStatus.ACTIVE, List.of(), c1.getId(), NOW));
        assertCode(
                () -> createRevision(true, List.of()),
                RevisionErrorCodes.CURRENT_ANNOTATION_REQUIRED);
    }

    private static boolean createConcurrently(
            CountDownLatch ready,
            CountDownLatch start) {
        ready.countDown();
        await(start);
        try {
            createRevision(false, List.of("article-1"));
            return true;
        } catch (ApiException exception) {
            assertThat(exception.getCode()).isEqualTo("TASK_ALREADY_EXISTS");
            return false;
        }
    }

    private static boolean submitConcurrently(
            String taskId,
            UserPrincipal annotator,
            CountDownLatch ready,
            CountDownLatch start) {
        ready.countDown();
        await(start);
        try {
            draftService.submitReview(taskId, annotator);
            return true;
        } catch (ApiException exception) {
            assertThat(exception.getCode())
                    .isIn("TASK.ALREADY_SUBMITTED", "ANNOTATION.TASK_NOT_EDITABLE");
            return false;
        }
    }

    private static boolean completeConcurrently(
            String taskId,
            String roundId,
            UserPrincipal reviewer,
            CountDownLatch ready,
            CountDownLatch start) {
        ready.countDown();
        await(start);
        try {
            reviewService.complete(taskId, roundId, reviewer);
            return true;
        } catch (ApiException exception) {
            assertThat(exception.getCode())
                    .isIn("REVIEW.ALREADY_COMPLETED", "REVIEW.WRITE_CONFLICT");
            return false;
        }
    }

    private static Scenario insertContentChangeScenario() {
        ContentVersionDocument c1 = content(
                "content-1", 1,
                List.of(
                        article("article-modified", "第一条", "旧正文", 0),
                        article("article-deleted", "第二条", "删除正文", 1),
                        article("article-unchanged", "第三条", "不变正文", 2)));
        ContentVersionDocument c2 = content(
                "content-2", 2,
                List.of(
                        article("article-modified", "第一条", "新正文", 0),
                        article("article-added", "第二条", "新增正文", 1),
                        article("article-unchanged", "第三条", "不变正文", 2)));
        Map<String, ArticleDraftValues> baseResults = new LinkedHashMap<>();
        baseResults.put("article-modified", annotation("旧标注", null));
        baseResults.put("article-deleted", annotation("删除标注", null));
        baseResults.put("article-unchanged", annotation("继承标注", null));
        PendingChangeSet changes = new PendingChangeSet(
                Set.of("article-added"),
                Set.of("article-modified"),
                Set.of("article-deleted"));
        insertBasis(c1, c2, baseResults, true, changes);
        return new Scenario(baseResults);
    }

    private static void insertAnnotationOnlyScenario() {
        ContentVersionDocument c1 = content(
                "content-1", 1,
                List.of(
                        article("article-1", "第一条", "正文一", 0),
                        article("article-2", "第二条", "正文二", 1)));
        Map<String, ArticleDraftValues> baseResults = new LinkedHashMap<>();
        baseResults.put("article-1", annotation("旧一", null));
        baseResults.put("article-2", annotation("旧二", null));
        insertBasis(c1, c1, baseResults, false, PendingChangeSet.empty());
    }

    private static void insertDeletionOnlyScenario() {
        ContentVersionDocument c1 = content(
                "content-1", 1,
                List.of(
                        article("article-kept", "第一条", "保留", 0),
                        article("article-deleted", "第二条", "删除", 1)));
        ContentVersionDocument c2 = content(
                "content-2", 2,
                List.of(article("article-kept", "第一条", "保留", 0)));
        Map<String, ArticleDraftValues> baseResults = new LinkedHashMap<>();
        baseResults.put("article-kept", annotation("保留", null));
        baseResults.put("article-deleted", annotation("删除", null));
        insertBasis(
                c1, c2, baseResults, true,
                new PendingChangeSet(Set.of(), Set.of(), Set.of("article-deleted")));
    }

    private static void insertModifiedOnlyScenario() {
        ContentVersionDocument c1 = content(
                "content-1", 1,
                List.of(article("article-1", "第一条", "修改前正文", 0)));
        ContentVersionDocument c2 = content(
                "content-2", 2,
                List.of(article("article-1", "第一条", "修改后正文", 0)));
        Map<String, ArticleDraftValues> baseResults = new LinkedHashMap<>();
        baseResults.put("article-1", annotation("修改前标注", null));
        insertBasis(
                c1,
                c2,
                baseResults,
                true,
                new PendingChangeSet(Set.of(), Set.of("article-1"), Set.of()));
    }

    private static void insertBasis(
            ContentVersionDocument c1,
            ContentVersionDocument latest,
            Map<String, ArticleDraftValues> baseResults,
            boolean pendingRevision,
            PendingChangeSet pendingChangeSet) {
        contentVersionRepository.insert(c1);
        if (!latest.getId().equals(c1.getId())) {
            contentVersionRepository.insert(latest);
        }
        annotationVersionRepository.insert(new AnnotationVersionDocument(
                "annotation-1", "law-1", 1, c1.getId(),
                new OverallDraftValues("民事", "基础", null, null),
                baseResults, "ordinary-task", "ordinary-submission", "admin-1", NOW));
        lawRepository.insert(new LawDocument(
                "law-1", "测试法", "测试法", "制定机关",
                LocalDate.of(2026, 8, 26), ValidityStatus.ACTIVE, List.of(), null,
                latest.getId(), "annotation-1", pendingRevision, pendingChangeSet, NOW, NOW));
    }

    private static com.law.annotation.task.dto.TaskDetailResponse createRevision(
            boolean overall,
            List<String> articleIds) {
        CreateRevisionTaskRequest request = new CreateRevisionTaskRequest();
        request.setLawId("law-1");
        request.setAnnotatorId("annotator-1");
        request.setOverall(overall);
        request.setArticleIds(articleIds);
        return revisionService.create(request, principal("admin-1", Role.ADMIN));
    }

    private static TaskDocument createdTask(boolean overall, List<String> articleIds) {
        String taskId = createRevision(overall, articleIds).taskId();
        return taskRepository.findById(taskId).orElseThrow();
    }

    private static SaveArticleDraftRequest articleRequest(String keyword, String subjects) {
        SaveArticleDraftRequest request = new SaveArticleDraftRequest();
        request.setItemType(ItemType.DEFINITION.name());
        request.setKeywords(keyword);
        request.setSubjects(subjects);
        return request;
    }

    private static ContentVersionDocument content(
            String id,
            int sequence,
            List<ArticleSnapshot> articles) {
        return new ContentVersionDocument(
                id, "law-1", sequence, articles, "admin-1", NOW.plusSeconds(sequence));
    }

    private static ArticleSnapshot article(
            String id,
            String number,
            String body,
            int order) {
        return new ArticleSnapshot(id, number, body, order);
    }

    private static ArticleDraftValues annotation(String keyword, String subjects) {
        return new ArticleDraftValues(
                ItemType.DEFINITION, keyword, subjects, null, null);
    }

    private static UserDocument user(String id, Role role, boolean enabled) {
        UserDocument user = new UserDocument(
                id, id, id, "$2a$12$hash", role, enabled, NOW, NOW);
        user.setId(id);
        return user;
    }

    private static UserPrincipal principal(String id, Role role) {
        return UserPrincipal.from(user(id, role, true));
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(code);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("并发测试等待超时");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private record Scenario(Map<String, ArticleDraftValues> baseResults) {
    }
}
