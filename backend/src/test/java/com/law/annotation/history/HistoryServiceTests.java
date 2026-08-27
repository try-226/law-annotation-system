package com.law.annotation.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.law.annotation.annotation.ArticleDraftValues;
import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.ItemType;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.field.FieldConfigSnapshot;
import com.law.annotation.history.dto.AnnotationVersionHistoryResponse;
import com.law.annotation.history.dto.LawHistoryResponse;
import com.law.annotation.law.ArticleSnapshot;
import com.law.annotation.law.LawAuditType;
import com.law.annotation.review.ReviewIssue;
import com.law.annotation.review.ReviewScopeType;
import com.law.annotation.task.TaskContentVersionSnapshot;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.task.TaskLawBaseInfoSnapshot;
import com.law.annotation.user.UserDocument;
import com.law.annotation.version.AnnotationVersionDocument;
import com.law.annotation.version.ContentVersionDocument;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HistoryServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-27T00:00:00Z");

    private HistoryQueryRepository repository;
    private HistoryService service;

    @BeforeEach
    void setUp() {
        repository = org.mockito.Mockito.mock(HistoryQueryRepository.class);
        service = new HistoryService(repository);
    }

    @Test
    void aggregatesEachSourceOnceAndSortsTimelineDeterministically() {
        when(repository.findLaw("law-1")).thenReturn(Optional.of(law()));
        when(repository.findContentSummaries("law-1")).thenReturn(List.of(
                new HistoryQueryRepository.ContentSummary("c-1", 1, "admin-1", NOW)));
        when(repository.findAuditSummaries("law-1")).thenReturn(List.of(
                new HistoryQueryRepository.AuditSummary(
                        "audit-1", LawAuditType.BASE_INFO, "admin-1", NOW)));
        when(repository.findAnnotationSummaries("law-1")).thenReturn(List.of());
        when(repository.findTaskSummaries("law-1")).thenReturn(List.of(
                new HistoryQueryRepository.TaskSummary(
                        "task-1", "任务一", "admin-1", NOW.minusSeconds(1), null, null, null)));
        when(repository.findSubmissionSummaries(List.of("task-1"))).thenReturn(List.of(
                new HistoryQueryRepository.SubmissionSummary(
                        "submission-1", "task-1", 1, null, "annotator-1", NOW.plusSeconds(1))));
        ReviewIssue issue = new ReviewIssue(
                "round-1", "task-1", ReviewScopeType.ARTICLE, "article-1", "需修改", NOW.plusSeconds(2));
        when(repository.findReviewSummaries(List.of("task-1"))).thenReturn(List.of(
                new HistoryQueryRepository.ReviewSummary(
                        "round-1", "task-1", 1, "admin-2", Map.of("issue", issue),
                        NOW.plusSeconds(1), NOW.plusSeconds(3))));

        LawHistoryResponse response = service.getLawHistory("law-1", principal("admin-1", Role.ADMIN));

        assertThat(response.timeline()).extracting(item -> item.type()).containsExactly(
                HistoryItemType.REVIEW_COMPLETED,
                HistoryItemType.REVIEW_ISSUE_CREATED,
                HistoryItemType.TASK_SUBMITTED,
                HistoryItemType.REVIEW_STARTED,
                HistoryItemType.CONTENT_VERSION_CREATED,
                HistoryItemType.LAW_METADATA_CHANGED,
                HistoryItemType.TASK_CREATED);
        verify(repository, times(1)).findContentSummaries("law-1");
        verify(repository, times(1)).findAuditSummaries("law-1");
        verify(repository, times(1)).findAnnotationSummaries("law-1");
        verify(repository, times(1)).findTaskSummaries("law-1");
        verify(repository, times(1)).findSubmissionSummaries(List.of("task-1"));
        verify(repository, times(1)).findReviewSummaries(List.of("task-1"));
    }

    @Test
    void annotatorCannotReadWholeLawTimeline() {
        assertThatThrownBy(() -> service.getLawHistory("law-1", principal("annotator-1", Role.ANNOTATOR)))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus().value()).isEqualTo(403);
                    assertThat(error.getCode()).isEqualTo("AUTH.FORBIDDEN");
                });
    }

    @Test
    void annotatorCanReadOwnTaskButOtherTaskIsHidden() {
        TaskDocument task = task("annotator-1");
        when(repository.findLaw("law-1")).thenReturn(Optional.of(law()));
        when(repository.findTask("law-1", "task-1")).thenReturn(Optional.of(task));
        when(repository.findTaskSubmissions("task-1")).thenReturn(List.of());
        when(repository.findTaskReviewRounds("task-1")).thenReturn(List.of());

        assertThat(service.getTask("law-1", "task-1", principal("annotator-1", Role.ANNOTATOR)).taskId())
                .isEqualTo("task-1");
        assertThatThrownBy(() -> service.getTask(
                "law-1", "task-1", principal("annotator-2", Role.ANNOTATOR)))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus().value()).isEqualTo(404);
                    assertThat(error.getCode()).isEqualTo("TASK.NOT_FOUND");
                });
    }

    @Test
    void annotationDetailUsesBoundHistoricalContentOrder() {
        ContentVersionDocument content = new ContentVersionDocument(
                "c-1", "law-1", 1,
                List.of(
                        new ArticleSnapshot("article-2", "第二条", "第二条内容", 0),
                        new ArticleSnapshot("article-1", "第一条", "第一条内容", 1)),
                "admin-1", NOW);
        Map<String, ArticleDraftValues> results = new LinkedHashMap<>();
        results.put("article-1", values("一"));
        results.put("article-2", values("二"));
        AnnotationVersionDocument annotation = new AnnotationVersionDocument(
                "a-1", "law-1", 1, "c-1", null, results,
                "task-1", "submission-1", "admin-1", NOW);
        when(repository.findLaw("law-1")).thenReturn(Optional.of(law()));
        when(repository.findAnnotationVersion("law-1", "a-1")).thenReturn(Optional.of(annotation));
        when(repository.findContentVersion("law-1", "c-1")).thenReturn(Optional.of(content));

        AnnotationVersionHistoryResponse response = service.getAnnotationVersion(
                "law-1", "a-1", principal("admin-1", Role.ADMIN));

        assertThat(response.articleResults()).extracting(AnnotationVersionHistoryResponse.ArticleResult::articleId)
                .containsExactly("article-2", "article-1");
    }

    private static HistoryQueryRepository.LawStatus law() {
        return new HistoryQueryRepository.LawStatus("law-1", null);
    }

    private static TaskDocument task(String annotatorId) {
        return new TaskDocument(
                "task-1", TaskType.ORDINARY, TaskState.PENDING_ANNOTATION,
                "law-1", annotatorId, "标注员", "任务一", null,
                "c-1", new TaskContentVersionSnapshot("c-1", 1, List.of()),
                new TaskLawBaseInfoSnapshot(
                        "测试法", "制定机关", LocalDate.of(2026, 8, 27), ValidityStatus.ACTIVE),
                List.of(), new FieldConfigSnapshot(List.of(), List.of()), "admin-1",
                null, null, null, null, NOW, NOW);
    }

    private static ArticleDraftValues values(String note) {
        return new ArticleDraftValues(ItemType.OTHER, null, null, null, note);
    }

    private static UserPrincipal principal(String id, Role role) {
        UserDocument user = new UserDocument(
                "用户", id, id, "$2a$12$hash", role, true, NOW, NOW);
        user.setId(id);
        return UserPrincipal.from(user);
    }
}
