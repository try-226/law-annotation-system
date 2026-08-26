package com.law.annotation.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.law.annotation.annotation.dto.SaveArticleDraftRequest;
import com.law.annotation.common.enums.ItemType;
import com.law.annotation.common.enums.ReviewItemState;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.field.FieldConfigService;
import com.law.annotation.law.LawOperationCoordinator;
import com.law.annotation.law.LawRepository;
import com.law.annotation.review.ReviewErrorCodes;
import com.law.annotation.review.ReviewIssue;
import com.law.annotation.review.ReviewItemLocator;
import com.law.annotation.review.ReviewRoundDocument;
import com.law.annotation.review.ReviewRoundOutcome;
import com.law.annotation.review.ReviewRoundRepository;
import com.law.annotation.review.ReviewRoundType;
import com.law.annotation.review.ReviewScopeType;
import com.law.annotation.review.ReviewService;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.task.TaskRepository;
import com.law.annotation.task.TaskService;
import com.law.annotation.user.UserRepository;
import com.law.annotation.version.AnnotationVersionRepository;
import com.law.annotation.version.ContentVersionRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

class RereviewDraftIntegrationTests {

    private static MongoServer mongoServer;
    private static MongoClient mongoClient;
    private static MongoTemplate mongoTemplate;
    private static TaskRepository taskRepository;
    private static TaskDraftRepository draftRepository;
    private static TaskSubmissionRepository submissionRepository;
    private static ReviewRoundRepository roundRepository;
    private static AnnotationDraftService draftService;
    private static ReviewService reviewService;

    @BeforeAll
    static void startMongo() throws Exception {
        mongoServer = new MongoServer(new MemoryBackend());
        mongoClient = MongoClients.create(mongoServer.bindAndGetConnectionString());
        mongoTemplate = new MongoTemplate(mongoClient, "rereview_draft_test");
        MongoRepositoryFactory factory = new MongoRepositoryFactory(mongoTemplate);
        taskRepository = factory.getRepository(TaskRepository.class);
        draftRepository = factory.getRepository(TaskDraftRepository.class);
        submissionRepository = factory.getRepository(TaskSubmissionRepository.class);
        roundRepository = factory.getRepository(ReviewRoundRepository.class);
        AnnotationVersionRepository versionRepository =
                factory.getRepository(AnnotationVersionRepository.class);
        new TaskSubmissionIndexInitializer(mongoTemplate)
                .run(new DefaultApplicationArguments());
        new RereviewSubmissionIndexInitializer(mongoTemplate)
                .run(new DefaultApplicationArguments());
        TaskService taskService = new TaskService(
                taskRepository,
                org.mockito.Mockito.mock(LawRepository.class),
                org.mockito.Mockito.mock(ContentVersionRepository.class),
                org.mockito.Mockito.mock(UserRepository.class),
                org.mockito.Mockito.mock(FieldConfigService.class),
                mongoTemplate,
                org.mockito.Mockito.mock(LawOperationCoordinator.class));
        reviewService = new ReviewService(
                taskRepository,
                submissionRepository,
                roundRepository,
                versionRepository,
                mongoTemplate);
        draftService = new AnnotationDraftService(
                taskRepository,
                draftRepository,
                submissionRepository,
                taskService,
                mongoTemplate,
                reviewService);
    }

    @AfterAll
    static void stopMongo() {
        mongoClient.close();
        mongoServer.shutdown();
    }

    @BeforeEach
    void setUpRejectedTask() {
        mongoTemplate.remove(new Query(), TaskDocument.class);
        mongoTemplate.remove(new Query(), TaskDraftDocument.class);
        mongoTemplate.remove(new Query(), TaskSubmissionDocument.class);
        mongoTemplate.remove(new Query(), ReviewRoundDocument.class);
        taskRepository.insert(AnnotationTestFixtures.task(
                TaskState.PARTIALLY_REJECTED,
                AnnotationTestFixtures.fieldSnapshot(),
                "submission-1"));
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is("task-1")),
                new Update().set("currentReviewRoundId", "round-1"),
                TaskDocument.class);

        Map<String, ArticleDraftValues> articles = new LinkedHashMap<>();
        articles.put("article-1", new ArticleDraftValues(
                ItemType.DEFINITION, "旧定义", null, null, null));
        articles.put("article-2", new ArticleDraftValues(
                ItemType.RIGHTS_DUTIES, "旧权利", null, null, null));
        draftRepository.insert(new TaskDraftDocument(
                "task-1",
                new OverallDraftValues("民事", "合同", null, null),
                articles,
                3,
                "annotator-1",
                AnnotationTestFixtures.NOW,
                AnnotationTestFixtures.NOW));
        submissionRepository.insert(new TaskSubmissionDocument(
                "submission-1",
                "task-1",
                1,
                3,
                new OverallDraftValues("民事", "合同", null, null),
                articles,
                "annotator-1",
                AnnotationTestFixtures.NOW));

        ReviewItemLocator overall = ReviewItemLocator.overall();
        ReviewItemLocator issueArticle = ReviewItemLocator.article("article-1");
        ReviewItemLocator checkedArticle = ReviewItemLocator.article("article-2");
        Map<String, ReviewItemState> states = new LinkedHashMap<>();
        states.put(overall.storageKey(), ReviewItemState.CHECKED);
        states.put(issueArticle.storageKey(), ReviewItemState.NEEDS_CHANGE);
        states.put(checkedArticle.storageKey(), ReviewItemState.CHECKED);
        ReviewIssue issue = new ReviewIssue(
                "round-1",
                "task-1",
                ReviewScopeType.ARTICLE,
                "article-1",
                "请修正定义标注",
                AnnotationTestFixtures.NOW);
        roundRepository.insert(new ReviewRoundDocument(
                "round-1",
                "task-1",
                "law-1",
                1,
                ReviewRoundType.INITIAL_REVIEW,
                "submission-1",
                null,
                "admin-1",
                List.of(overall, issueArticle, checkedArticle),
                states,
                Map.of(issueArticle.storageKey(), issue),
                3,
                3,
                0,
                1,
                ReviewRoundOutcome.PARTIALLY_REJECTED,
                AnnotationTestFixtures.NOW,
                null,
                AnnotationTestFixtures.NOW,
                AnnotationTestFixtures.NOW,
                AnnotationTestFixtures.NOW.plusSeconds(1)));
    }

    @Test
    void onlyIssueScopeCanBeSavedAndEveryIssueMustBeResavedBeforeRereview() {
        var owner = AnnotationTestFixtures.principal("annotator-1", Role.ANNOTATOR);
        var initialDraft = draftService.getDraft("task-1", owner);
        assertThat(initialDraft.editableScope().overallEditable()).isFalse();
        assertThat(initialDraft.editableScope().editableArticleIds())
                .containsExactly("article-1");
        assertThat(initialDraft.reviewIssues()).singleElement().satisfies(feedback -> {
            assertThat(feedback.locator()).isEqualTo(
                    ReviewItemLocator.article("article-1"));
            assertThat(feedback.reason()).isEqualTo("请修正定义标注");
        });

        assertThatThrownBy(() -> draftService.saveArticle(
                        "task-1", "article-2", completeArticle("越权修改"), owner))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(AnnotationErrorCodes.TASK_NOT_EDITABLE);
        assertThatThrownBy(() -> draftService.submitRereview("task-1", owner))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(AnnotationErrorCodes.REREVIEW_SCOPE_NOT_RESAVED);

        draftService.saveArticle(
                "task-1", "article-1", completeArticle("修正后的定义"), owner);
        var response = draftService.submitRereview("task-1", owner);

        assertThat(response.taskState()).isEqualTo(TaskState.PENDING_REREVIEW);
        TaskSubmissionDocument rereview = submissionRepository
                .findById(response.submissionId()).orElseThrow();
        assertThat(rereview.getSubmissionNo()).isEqualTo(2);
        assertThat(rereview.getSourceReviewRoundId()).isEqualTo("round-1");
        assertThat(rereview.getModifiedScope())
                .containsExactly(ReviewItemLocator.article("article-1"));
        assertThat(rereview.getArticleSnapshots().get("article-1").keywords())
                .isEqualTo("修正后的定义");
        assertThat(rereview.getArticleSnapshots().get("article-2").keywords())
                .isEqualTo("旧权利");
        TaskDocument task = taskRepository.findById("task-1").orElseThrow();
        assertThat(task.getTaskState()).isEqualTo(TaskState.PENDING_REREVIEW);
        assertThat(task.getCurrentSubmissionId()).isEqualTo(rereview.getSubmissionId());
        assertThat(task.getCurrentReviewRoundId()).isNull();
        var admin2 = AnnotationTestFixtures.principal("admin-2", Role.ADMIN);
        assertThatThrownBy(() -> reviewService.getReview("task-1", admin2))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ReviewErrorCodes.NOT_STARTED);

        var started = reviewService.start("task-1", admin2);
        ReviewRoundDocument secondRound = roundRepository
                .findById(started.reviewRoundId()).orElseThrow();
        assertThat(secondRound.getRoundType()).isEqualTo(ReviewRoundType.REREVIEW);
        assertThat(secondRound.getReviewerId()).isEqualTo("admin-2");
        assertThat(taskRepository.findById("task-1").orElseThrow()
                .getCurrentReviewRoundId()).isEqualTo(secondRound.getReviewRoundId());

        ReviewRoundDocument firstRound = roundRepository.findById("round-1").orElseThrow();
        assertThat(firstRound.getReviewerId()).isEqualTo("admin-1");
        assertThat(firstRound.getCompletedAt()).isNotNull();
        assertThat(rereview.getSourceReviewRoundId()).isEqualTo(firstRound.getReviewRoundId());

        var admin3 = AnnotationTestFixtures.principal("admin-3", Role.ADMIN);
        assertThatThrownBy(() -> reviewService.check(
                        "task-1",
                        secondRound.getReviewRoundId(),
                        ReviewItemLocator.article("article-1"),
                        admin3))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ReviewErrorCodes.NOT_REVIEWER);
        assertThat(roundRepository.findById("round-1").orElseThrow().getReviewerId())
                .isEqualTo("admin-1");
    }

    private static SaveArticleDraftRequest completeArticle(String keywords) {
        SaveArticleDraftRequest request = new SaveArticleDraftRequest();
        request.setItemType("DEFINITION");
        request.setKeywords(keywords);
        return request;
    }
}
