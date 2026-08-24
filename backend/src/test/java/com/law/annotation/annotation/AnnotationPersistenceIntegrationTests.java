package com.law.annotation.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.law.annotation.annotation.dto.SaveArticleDraftRequest;
import com.law.annotation.annotation.dto.SaveOverallDraftRequest;
import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.field.FieldConfigService;
import com.law.annotation.law.LawOperationCoordinator;
import com.law.annotation.law.LawRepository;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.task.TaskErrorCodes;
import com.law.annotation.task.TaskRepository;
import com.law.annotation.task.TaskService;
import com.law.annotation.user.UserRepository;
import com.law.annotation.version.ContentVersionRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import java.util.List;
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

class AnnotationPersistenceIntegrationTests {

    private static MongoServer mongoServer;
    private static MongoClient mongoClient;
    private static MongoTemplate mongoTemplate;
    private static TaskRepository taskRepository;
    private static TaskDraftRepository draftRepository;
    private static TaskSubmissionRepository submissionRepository;
    private static AnnotationDraftService service;

    @BeforeAll
    static void startMongo() throws Exception {
        mongoServer = new MongoServer(new MemoryBackend());
        mongoClient = MongoClients.create(mongoServer.bindAndGetConnectionString());
        mongoTemplate = new MongoTemplate(mongoClient, "annotation_workflow_test");
        MongoRepositoryFactory factory = new MongoRepositoryFactory(mongoTemplate);
        taskRepository = factory.getRepository(TaskRepository.class);
        draftRepository = factory.getRepository(TaskDraftRepository.class);
        submissionRepository = factory.getRepository(TaskSubmissionRepository.class);
        new TaskSubmissionIndexInitializer(mongoTemplate)
                .run(new DefaultApplicationArguments());
        TaskService taskService = new TaskService(
                taskRepository,
                org.mockito.Mockito.mock(LawRepository.class),
                org.mockito.Mockito.mock(ContentVersionRepository.class),
                org.mockito.Mockito.mock(UserRepository.class),
                org.mockito.Mockito.mock(FieldConfigService.class),
                mongoTemplate,
                org.mockito.Mockito.mock(LawOperationCoordinator.class));
        service = new AnnotationDraftService(
                taskRepository,
                draftRepository,
                submissionRepository,
                taskService,
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
        mongoTemplate.remove(new Query(), TaskDraftDocument.class);
        mongoTemplate.remove(new Query(), TaskSubmissionDocument.class);
        taskRepository.insert(AnnotationTestFixtures.task(TaskState.ANNOTATING));
    }

    @Test
    void overallAndArticleDraftsPersistIndependentlyAndClearUpdatesProgress() {
        UserPrincipal owner = owner();
        SaveOverallDraftRequest overall = new SaveOverallDraftRequest();
        overall.setLawCategory("民事");
        overall.setOverallKeywords("合同");
        service.saveOverall("task-1", overall, owner);

        SaveArticleDraftRequest first = completeArticle("DEFINITION", "定义");
        SaveArticleDraftRequest second = completeArticle("RIGHTS_DUTIES", "权利");
        service.saveArticle("task-1", "article-1", first, owner);
        var complete = service.saveArticle("task-1", "article-2", second, owner);

        assertThat(complete.progress().overallCompleted()).isTrue();
        assertThat(complete.progress().totalArticles()).isEqualTo(2);
        assertThat(complete.progress().filledArticles()).isEqualTo(2);
        assertThat(draftRepository.findById("task-1").orElseThrow().getRevision())
                .isEqualTo(3);

        var cleared = service.clearArticle("task-1", "article-1", owner);
        assertThat(cleared.progress().filledArticles()).isEqualTo(1);
        assertThat(cleared.articleDrafts()).doesNotContainKey("article-1");
        assertThat(service.getDraft("task-1", owner).progress().filledArticles())
                .isEqualTo(1);

        var overallCleared = service.clearOverall("task-1", owner);
        assertThat(overallCleared.progress().overallCompleted()).isFalse();
        assertThat(overallCleared.overallDraft()).isNull();
    }

    @Test
    void submitFreezesDraftAndTransitionsTaskWithoutFormalAnnotation() {
        saveCompleteDraft();

        var response = service.submitReview("task-1", owner());

        assertThat(response.taskState()).isEqualTo(TaskState.PENDING_REVIEW);
        assertThat(taskRepository.findById("task-1").orElseThrow().getTaskState())
                .isEqualTo(TaskState.PENDING_REVIEW);
        TaskSubmissionDocument submission = submissionRepository.findAll().getFirst();
        assertThat(taskRepository.findById("task-1").orElseThrow().getInitialSubmissionId())
                .isEqualTo(submission.getSubmissionId());
        assertThat(submission).satisfies(saved -> {
            assertThat(saved.getSubmissionNo()).isEqualTo(1);
            assertThat(saved.getArticleSnapshots()).containsOnlyKeys("article-1", "article-2");
            assertThat(saved.getOverallSnapshot().overallKeywords()).isEqualTo("合同");
        });
        assertThat(mongoTemplate.collectionExists("annotation_versions")).isFalse();
    }

    @Test
    void submittedSnapshotDoesNotChangeWhenDraftDocumentChanges() {
        saveCompleteDraft();
        var response = service.submitReview("task-1", owner());

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is("task-1")),
                new Update()
                        .set("overallDraft.overallKeywords", "修改后的关键词")
                        .set("perArticleDrafts.article-1.keywords", "修改后的法条关键词"),
                TaskDraftDocument.class);

        TaskSubmissionDocument submission = submissionRepository
                .findById(response.submissionId())
                .orElseThrow();
        assertThat(submission.getOverallSnapshot().overallKeywords()).isEqualTo("合同");
        assertThat(submission.getArticleSnapshots().get("article-1").keywords())
                .isEqualTo("定义");
    }

    @Test
    void predictableTaskTransitionFailureLeavesNoSubmissionAndKeepsTaskAnnotating() {
        saveCompleteDraft();
        TaskService failingTaskService = org.mockito.Mockito.mock(TaskService.class);
        org.mockito.Mockito.when(failingTaskService.submitReview(
                        org.mockito.ArgumentMatchers.eq("task-1"),
                        org.mockito.ArgumentMatchers.eq("annotator-1"),
                        org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new ApiException(
                        org.springframework.http.HttpStatus.CONFLICT,
                        TaskErrorCodes.INVALID_STATE_TRANSITION,
                        "任务状态不允许提交审核"));
        AnnotationDraftService failingService = new AnnotationDraftService(
                taskRepository,
                draftRepository,
                submissionRepository,
                failingTaskService,
                mongoTemplate);

        assertThatThrownBy(() -> failingService.submitReview("task-1", owner()))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(TaskErrorCodes.INVALID_STATE_TRANSITION);

        assertThat(taskRepository.findById("task-1").orElseThrow().getTaskState())
                .isEqualTo(TaskState.ANNOTATING);
        assertThat(submissionRepository.count()).isZero();
    }

    @Test
    void concurrentSubmitCreatesExactlyOneSubmissionAndOneStateTransition() throws Exception {
        saveCompleteDraft();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> submitAfterBarrier(ready, start));
            Future<String> second = executor.submit(() -> submitAfterBarrier(ready, start));
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder("SUCCESS", TaskErrorCodes.ALREADY_SUBMITTED);
            assertThat(submissionRepository.count()).isEqualTo(1);
            assertThat(taskRepository.findById("task-1").orElseThrow().getTaskState())
                    .isEqualTo(TaskState.PENDING_REVIEW);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void saveCompleteDraft() {
        UserPrincipal owner = owner();
        SaveOverallDraftRequest overall = new SaveOverallDraftRequest();
        overall.setLawCategory("民事");
        overall.setOverallKeywords("合同");
        service.saveOverall("task-1", overall, owner);
        service.saveArticle(
                "task-1", "article-1", completeArticle("DEFINITION", "定义"), owner);
        service.saveArticle(
                "task-1", "article-2", completeArticle("RIGHTS_DUTIES", "权利"), owner);
    }

    private static SaveArticleDraftRequest completeArticle(String itemType, String keywords) {
        SaveArticleDraftRequest request = new SaveArticleDraftRequest();
        request.setItemType(itemType);
        request.setKeywords(keywords);
        return request;
    }

    private static String submitAfterBarrier(
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            service.submitReview("task-1", owner());
            return "SUCCESS";
        } catch (ApiException exception) {
            return exception.getCode();
        }
    }

    private static UserPrincipal owner() {
        return AnnotationTestFixtures.principal("annotator-1", Role.ANNOTATOR);
    }
}
