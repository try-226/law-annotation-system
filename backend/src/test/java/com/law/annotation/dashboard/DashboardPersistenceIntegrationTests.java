package com.law.annotation.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.dashboard.dto.DashboardSummaryResponse;
import com.law.annotation.dashboard.dto.DashboardTodoResponse;
import com.law.annotation.field.FieldConfigSnapshot;
import com.law.annotation.law.ArticleSnapshot;
import com.law.annotation.law.LawDisplayStatusResolver;
import com.law.annotation.law.LawDocument;
import com.law.annotation.law.LawRepository;
import com.law.annotation.law.PendingChangeSet;
import com.law.annotation.task.TaskContentVersionSnapshot;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.task.TaskLawBaseInfoSnapshot;
import com.law.annotation.task.TaskRepository;
import com.law.annotation.version.ContentVersionDocument;
import com.law.annotation.version.ContentVersionRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;

class DashboardPersistenceIntegrationTests {

    private static final Instant T0 = Instant.parse("2026-08-27T00:00:00Z");
    private static MongoServer mongoServer;
    private static MongoClient mongoClient;
    private static MongoTemplate mongoTemplate;
    private static LawRepository lawRepository;
    private static ContentVersionRepository contentVersionRepository;
    private static TaskRepository taskRepository;
    private static DashboardService service;

    @BeforeAll
    static void startMongo() {
        mongoServer = new MongoServer(new MemoryBackend());
        mongoClient = MongoClients.create(mongoServer.bindAndGetConnectionString());
        mongoTemplate = new MongoTemplate(mongoClient, "dashboard_pr21_test");
        MongoRepositoryFactory factory = new MongoRepositoryFactory(mongoTemplate);
        lawRepository = factory.getRepository(LawRepository.class);
        contentVersionRepository = factory.getRepository(ContentVersionRepository.class);
        taskRepository = factory.getRepository(TaskRepository.class);
        service = new DashboardService(
                lawRepository,
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
    void clearData() {
        mongoTemplate.remove(new Query(), LawDocument.class);
        mongoTemplate.remove(new Query(), ContentVersionDocument.class);
        mongoTemplate.remove(new Query(), TaskDocument.class);
        mongoTemplate.getCollection("annotation_versions").deleteMany(new org.bson.Document());
        mongoTemplate.getCollection("review_rounds").deleteMany(new org.bson.Document());
        mongoTemplate.getCollection("law_audits").deleteMany(new org.bson.Document());
    }

    @Test
    void summaryAndTodosUseCurrentVisibleDataWithNoWriteSideEffects() {
        contentVersionRepository.insert(version("c-a-old", "law-a", 1, 2));
        ContentVersionDocument currentA = version("c-a-current", "law-a", 2, 3);
        ContentVersionDocument currentB = version("c-b", "law-b", 1, 4);
        ContentVersionDocument currentC = version("c-c", "law-c", 1, 1);
        ContentVersionDocument currentD = version("c-d", "law-d", 1, 1);
        ContentVersionDocument currentE = version("c-e", "law-e", 1, 1);
        ContentVersionDocument deletedVersion = version("c-deleted", "law-deleted", 1, 6);
        List.of(currentA, currentB, currentC, currentD, currentE, deletedVersion)
                .forEach(contentVersionRepository::insert);

        LawDocument lawA = law("law-a", currentA.getId(), null, false);
        LawDocument lawB = law("law-b", currentB.getId(), "a-b", false);
        LawDocument lawC = law("law-c", currentC.getId(), "a-c", true);
        LawDocument lawD = law("law-d", currentD.getId(), null, false);
        LawDocument lawE = law("law-e", currentE.getId(), "a-e", true);
        LawDocument deletedLaw = law("law-deleted", deletedVersion.getId(), null, false);
        deletedLaw.markDeleted(T0.plusSeconds(100));
        List.of(lawA, lawB, lawC, lawD, lawE, deletedLaw).forEach(lawRepository::insert);

        taskRepository.insert(task(
                "task-review", lawD, TaskType.ORDINARY, TaskState.PENDING_REVIEW,
                T0.plusSeconds(10)));
        taskRepository.insert(task(
                "task-rereview", lawE, TaskType.REVISION, TaskState.PENDING_REREVIEW,
                T0.plusSeconds(20)));
        taskRepository.insert(task(
                "task-deleted", deletedLaw, TaskType.ORDINARY, TaskState.PENDING_REVIEW,
                T0.plusSeconds(30)));

        Map<String, Long> before = collectionCounts();

        DashboardSummaryResponse summary = service.getSummary();
        DashboardTodoResponse todos = service.getTodos();

        assertThat(summary.totalLaws()).isEqualTo(5);
        assertThat(summary.totalArticles()).isEqualTo(10);
        assertThat(summary.unannotatedLaws()).isEqualTo(1);
        assertThat(summary.inProgressTasks()).isEqualTo(2);
        assertThat(summary.pendingReviewTasks()).isEqualTo(1);
        assertThat(summary.pendingRereviewTasks()).isEqualTo(1);
        assertThat(summary.pendingRevisionLaws()).isEqualTo(1);
        assertThat(summary.completedLaws()).isEqualTo(1);
        assertThat(todos.pendingReview()).extracting(item -> item.taskId())
                .containsExactly("task-review");
        assertThat(todos.pendingRereview()).extracting(item -> item.taskId())
                .containsExactly("task-rereview");
        assertThat(todos.pendingReview()).extracting(item -> item.lawId())
                .doesNotContain("law-deleted");
        assertThat(collectionCounts()).isEqualTo(before);
    }

    @Test
    void todosAreLimitedToTenNewestTasksPerCategory() {
        for (int index = 0; index < 12; index++) {
            String suffix = String.format("%02d", index);
            ContentVersionDocument reviewVersion = version(
                    "c-review-" + suffix, "law-review-" + suffix, 1, 1);
            ContentVersionDocument rereviewVersion = version(
                    "c-rereview-" + suffix, "law-rereview-" + suffix, 1, 1);
            contentVersionRepository.insert(reviewVersion);
            contentVersionRepository.insert(rereviewVersion);
            LawDocument reviewLaw = law(
                    "law-review-" + suffix, reviewVersion.getId(), null, false);
            LawDocument rereviewLaw = law(
                    "law-rereview-" + suffix, rereviewVersion.getId(), null, false);
            lawRepository.insert(reviewLaw);
            lawRepository.insert(rereviewLaw);
            taskRepository.insert(task(
                    "review-" + suffix,
                    reviewLaw,
                    TaskType.ORDINARY,
                    TaskState.PENDING_REVIEW,
                    T0.plusSeconds(index / 2)));
            taskRepository.insert(task(
                    "rereview-" + suffix,
                    rereviewLaw,
                    TaskType.REVISION,
                    TaskState.PENDING_REREVIEW,
                    T0.plusSeconds(index / 2)));
        }

        DashboardTodoResponse todos = service.getTodos();

        assertThat(todos.pendingReview()).hasSize(10);
        assertThat(todos.pendingReview()).extracting(item -> item.taskId())
                .containsExactly(
                        "review-11", "review-10", "review-09", "review-08", "review-07",
                        "review-06", "review-05", "review-04", "review-03", "review-02");
        assertThat(todos.pendingRereview()).hasSize(10);
        assertThat(todos.pendingRereview()).extracting(item -> item.taskId())
                .containsExactly(
                        "rereview-11", "rereview-10", "rereview-09", "rereview-08",
                        "rereview-07", "rereview-06", "rereview-05", "rereview-04",
                        "rereview-03", "rereview-02");
    }

    @Test
    void inProgressCountIncludesAllFiveNonTerminalStatesAndExcludesTerminalStates() {
        TaskState[] states = TaskState.values();
        for (int index = 0; index < states.length; index++) {
            String suffix = String.format("%02d", index);
            ContentVersionDocument version = version(
                    "c-state-" + suffix, "law-state-" + suffix, 1, 1);
            contentVersionRepository.insert(version);
            LawDocument law = law(
                    "law-state-" + suffix, version.getId(), null, false);
            lawRepository.insert(law);
            taskRepository.insert(task(
                    "task-state-" + suffix,
                    law,
                    TaskType.ORDINARY,
                    states[index],
                    T0.plusSeconds(index)));
        }

        DashboardSummaryResponse summary = service.getSummary();

        assertThat(summary.inProgressTasks()).isEqualTo(5);
        assertThat(summary.pendingReviewTasks()).isEqualTo(1);
        assertThat(summary.pendingRereviewTasks()).isEqualTo(1);
    }

    private static Map<String, Long> collectionCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        List.of(
                        "laws",
                        "content_versions",
                        "tasks",
                        "annotation_versions",
                        "review_rounds",
                        "law_audits")
                .forEach(collection -> counts.put(
                        collection,
                        mongoTemplate.getCollection(collection).countDocuments()));
        return counts;
    }

    private static LawDocument law(
            String id,
            String contentVersionId,
            String annotationVersionId,
            boolean pendingRevision) {
        return new LawDocument(
                id,
                id + "名称",
                id + "名称",
                "制定机关",
                LocalDate.of(2026, 8, 27),
                ValidityStatus.ACTIVE,
                List.of(),
                null,
                contentVersionId,
                annotationVersionId,
                pendingRevision,
                pendingRevision
                        ? new PendingChangeSet(Set.of(), Set.of("article-0"), Set.of())
                        : PendingChangeSet.empty(),
                T0,
                T0);
    }

    private static ContentVersionDocument version(
            String id,
            String lawId,
            int sequence,
            int articleCount) {
        List<ArticleSnapshot> articles = new ArrayList<>();
        for (int index = 0; index < articleCount; index++) {
            articles.add(new ArticleSnapshot(
                    "article-" + index,
                    "第" + (index + 1) + "条",
                    "正文" + index,
                    index));
        }
        return new ContentVersionDocument(id, lawId, sequence, articles, "admin", T0);
    }

    private static TaskDocument task(
            String taskId,
            LawDocument law,
            TaskType type,
            TaskState state,
            Instant createdAt) {
        return new TaskDocument(
                taskId,
                type,
                state,
                law.getId(),
                "annotator",
                "标注员",
                taskId + "名称",
                null,
                law.getCurrentContentVersionId(),
                new TaskContentVersionSnapshot(
                        law.getCurrentContentVersionId(), 1, List.of()),
                TaskLawBaseInfoSnapshot.from(law),
                List.of(),
                new FieldConfigSnapshot(List.of(), List.of()),
                "admin",
                null,
                null,
                null,
                null,
                createdAt,
                createdAt);
    }
}
