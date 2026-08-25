package com.law.annotation.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.common.response.PageResponse;
import com.law.annotation.law.dto.LawListItemResponse;
import com.law.annotation.task.TaskRepository;
import com.law.annotation.task.TaskStateRules;
import com.law.annotation.task.TaskStatusProjection;
import com.law.annotation.version.ContentVersionDocument;
import com.law.annotation.version.ContentVersionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class LawQueryServiceTests {

    private LawRepository lawRepository;
    private LawSearchRepository lawSearchRepository;
    private ContentVersionRepository contentVersionRepository;
    private TaskRepository taskRepository;
    private LawQueryService service;

    @BeforeEach
    void setUp() {
        lawRepository = mock(LawRepository.class);
        lawSearchRepository = mock(LawSearchRepository.class);
        contentVersionRepository = mock(ContentVersionRepository.class);
        taskRepository = mock(TaskRepository.class);
        service = new LawQueryService(
                lawRepository,
                lawSearchRepository,
                contentVersionRepository,
                taskRepository,
                new LawDisplayStatusResolver());
    }

    @Test
    void mapsOnePageWithOneBatchTaskQueryInsteadOfQueryingPerLaw() {
        LawDocument first = law("law-1", "content-1");
        LawDocument second = law("law-2", "content-2");
        ContentVersionDocument firstVersion = version("content-1", "law-1");
        ContentVersionDocument secondVersion = version("content-2", "law-2");
        TaskStatusProjection firstTask = task(
                "law-1", TaskType.ORDINARY, TaskState.PENDING_ANNOTATION);
        TaskStatusProjection secondTask = task(
                "law-2", TaskType.REVISION, TaskState.ANNOTATING);
        when(lawSearchRepository.search(any(), any()))
                .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 10), 2));
        when(contentVersionRepository.findByIdIn(List.of("content-1", "content-2")))
                .thenReturn(List.of(firstVersion, secondVersion));
        when(taskRepository.findStatusesByLawIdInAndTaskStateIn(
                List.of("law-1", "law-2"),
                TaskStateRules.unfinishedStates()))
                .thenReturn(List.of(firstTask, secondTask));

        PageResponse<LawListItemResponse> result = service.list(null, null, null, 0, 10);

        assertThat(result.items()).extracting(LawListItemResponse::displayStatus)
                .containsExactly(LawDisplayStatus.ANNOTATING, LawDisplayStatus.REVISING);
        verify(taskRepository).findStatusesByLawIdInAndTaskStateIn(
                List.of("law-1", "law-2"),
                TaskStateRules.unfinishedStates());
        verify(taskRepository, never()).findStatusesByTaskStateIn(any());
    }

    @Test
    void classifiesMatchingLawIdsByTaskTypeAndStateBeforePaging() {
        TaskStatusProjection ordinary = task(
                "ordinary-law", TaskType.ORDINARY, TaskState.PENDING_ANNOTATION);
        TaskStatusProjection revision = task(
                "revision-law", TaskType.REVISION, TaskState.PENDING_ANNOTATION);
        when(taskRepository.findStatusesByTaskStateIn(TaskStateRules.unfinishedStates()))
                .thenReturn(List.of(ordinary, revision));
        LawDocument law = law("ordinary-law", "content-1");
        ContentVersionDocument contentVersion = version("content-1", "ordinary-law");
        when(lawSearchRepository.search(any(), any()))
                .thenReturn(new PageImpl<>(List.of(law), PageRequest.of(0, 10), 1));
        when(contentVersionRepository.findByIdIn(List.of("content-1")))
                .thenReturn(List.of(contentVersion));

        PageResponse<LawListItemResponse> result = service.list(
                null, null, LawDisplayStatus.ANNOTATING, 0, 10);

        ArgumentCaptor<LawSearchFilter> filterCaptor = ArgumentCaptor.forClass(LawSearchFilter.class);
        verify(lawSearchRepository).search(filterCaptor.capture(), any());
        assertThat(filterCaptor.getValue().includeLawIds()).containsExactly("ordinary-law");
        assertThat(filterCaptor.getValue().excludeLawIds())
                .containsExactlyInAnyOrder("ordinary-law", "revision-law");
        assertThat(result.items()).extracting(LawListItemResponse::displayStatus)
                .containsExactly(LawDisplayStatus.ANNOTATING);
    }

    @Test
    void returnsAnEmptyPageWhenAnActiveDisplayStatusHasNoMatchingLawIds() {
        when(taskRepository.findStatusesByTaskStateIn(TaskStateRules.unfinishedStates()))
                .thenReturn(List.of());

        PageResponse<LawListItemResponse> result = service.list(
                null, null, LawDisplayStatus.PENDING_REVIEW, 0, 10);

        assertThat(result.items()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
        verify(lawSearchRepository, never()).search(any(), any());
        verify(contentVersionRepository, never()).findByIdIn(any());
    }

    @Test
    void revisingFilterIncludesEveryUnfinishedRevisionTaskState() {
        List<TaskStatusProjection> revisionTasks = List.of(
                task("revision-pending", TaskType.REVISION, TaskState.PENDING_ANNOTATION),
                task("revision-annotating", TaskType.REVISION, TaskState.ANNOTATING),
                task("revision-review", TaskType.REVISION, TaskState.PENDING_REVIEW),
                task("revision-rejected", TaskType.REVISION, TaskState.PARTIALLY_REJECTED),
                task("revision-rereview", TaskType.REVISION, TaskState.PENDING_REREVIEW));
        when(taskRepository.findStatusesByTaskStateIn(TaskStateRules.unfinishedStates()))
                .thenReturn(revisionTasks);
        when(lawSearchRepository.search(any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        service.list(null, null, LawDisplayStatus.REVISING, 0, 10);

        ArgumentCaptor<LawSearchFilter> filterCaptor = ArgumentCaptor.forClass(LawSearchFilter.class);
        verify(lawSearchRepository).search(filterCaptor.capture(), any());
        assertThat(filterCaptor.getValue().includeLawIds()).containsExactlyInAnyOrder(
                "revision-pending",
                "revision-annotating",
                "revision-review",
                "revision-rejected",
                "revision-rereview");
    }

    @Test
    void revisionReviewStatesDoNotMatchOrdinaryReviewDisplayFilters() {
        List<TaskStatusProjection> revisionReviewTasks = List.of(
                task("revision-review", TaskType.REVISION, TaskState.PENDING_REVIEW),
                task("revision-rejected", TaskType.REVISION, TaskState.PARTIALLY_REJECTED),
                task("revision-rereview", TaskType.REVISION, TaskState.PENDING_REREVIEW));
        when(taskRepository.findStatusesByTaskStateIn(TaskStateRules.unfinishedStates()))
                .thenReturn(revisionReviewTasks);
        when(lawSearchRepository.search(any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        for (LawDisplayStatus displayStatus : List.of(
                LawDisplayStatus.PENDING_REVIEW,
                LawDisplayStatus.PARTIALLY_REJECTED,
                LawDisplayStatus.PENDING_REREVIEW)) {
            PageResponse<LawListItemResponse> result = service.list(
                    null, null, displayStatus, 0, 10);
            assertThat(result.items()).isEmpty();
        }

        verify(lawSearchRepository, never()).search(any(), any());
    }

    private static LawDocument law(String lawId, String contentVersionId) {
        LawDocument law = mock(LawDocument.class);
        when(law.getId()).thenReturn(lawId);
        when(law.getName()).thenReturn(lawId + "名称");
        when(law.getIssuingAuthority()).thenReturn("制定机关");
        when(law.getValidityStatus()).thenReturn(com.law.annotation.common.enums.ValidityStatus.ACTIVE);
        when(law.getCurrentContentVersionId()).thenReturn(contentVersionId);
        when(law.getUpdatedAt()).thenReturn(Instant.parse("2026-08-25T00:00:00Z"));
        return law;
    }

    private static ContentVersionDocument version(String versionId, String lawId) {
        ContentVersionDocument version = mock(ContentVersionDocument.class);
        when(version.getId()).thenReturn(versionId);
        when(version.getLawId()).thenReturn(lawId);
        when(version.getSemanticArticlesSnapshot()).thenReturn(List.of());
        return version;
    }

    private static TaskStatusProjection task(
            String lawId,
            TaskType type,
            TaskState state) {
        TaskStatusProjection task = mock(TaskStatusProjection.class);
        when(task.getLawId()).thenReturn(lawId);
        when(task.getTaskType()).thenReturn(type);
        when(task.getTaskState()).thenReturn(state);
        return task;
    }
}
