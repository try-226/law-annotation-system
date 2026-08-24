package com.law.annotation.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.law.annotation.annotation.dto.SaveOverallDraftRequest;
import com.law.annotation.annotation.dto.SubmitReviewResponse;
import com.law.annotation.annotation.dto.TaskDraftResponse;
import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.ItemType;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.task.TaskErrorCodes;
import com.law.annotation.task.TaskRepository;
import com.law.annotation.task.TaskService;
import com.law.annotation.task.dto.TaskDetailResponse;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;

class AnnotationDraftServiceTests {

    private final TaskRepository taskRepository = org.mockito.Mockito.mock(TaskRepository.class);
    private final TaskDraftRepository draftRepository = org.mockito.Mockito.mock(TaskDraftRepository.class);
    private final TaskSubmissionRepository submissionRepository =
            org.mockito.Mockito.mock(TaskSubmissionRepository.class);
    private final TaskService taskService = org.mockito.Mockito.mock(TaskService.class);
    private final MongoTemplate mongoTemplate = org.mockito.Mockito.mock(MongoTemplate.class);

    private AnnotationDraftService service;

    @BeforeEach
    void setUp() {
        service = new AnnotationDraftService(
                taskRepository,
                draftRepository,
                submissionRepository,
                taskService,
                mongoTemplate);
    }

    @Test
    void ownerSavesOverallDraftAndGetsSnapshotBasedProgress() {
        TaskDocument task = AnnotationTestFixtures.task(TaskState.ANNOTATING);
        UserPrincipal owner = AnnotationTestFixtures.principal("annotator-1", Role.ANNOTATOR);
        TaskDraftDocument stored = completeDraft();
        when(taskRepository.findByTaskIdAndAnnotatorId("task-1", "annotator-1"))
                .thenReturn(Optional.of(task));
        when(draftRepository.findById("task-1")).thenReturn(Optional.of(stored));
        SaveOverallDraftRequest request = new SaveOverallDraftRequest();
        request.setLawCategory("民事");
        request.setOverallKeywords("合同");

        TaskDraftResponse response = service.saveOverall("task-1", request, owner);

        assertThat(response.progress().totalArticles()).isEqualTo(2);
        assertThat(response.progress().filledArticles()).isEqualTo(2);
        assertThat(response.progress().overallCompleted()).isTrue();
        assertThat(response.editableScope().editableArticleIds())
                .containsExactly("article-1", "article-2");
        verify(mongoTemplate).upsert(any(), any(),
                org.mockito.ArgumentMatchers.eq(TaskDraftDocument.class));
    }

    @Test
    void adminCanReadButCannotWriteDraft() {
        TaskDocument task = AnnotationTestFixtures.task(TaskState.ANNOTATING);
        UserPrincipal admin = AnnotationTestFixtures.principal("admin-1", Role.ADMIN);
        when(taskRepository.findById("task-1")).thenReturn(Optional.of(task));
        when(draftRepository.findById("task-1")).thenReturn(Optional.of(completeDraft()));

        TaskDraftResponse response = service.getDraft("task-1", admin);
        assertThat(response.editableScope().overallEditable()).isFalse();

        assertCode(
                () -> service.saveOverall("task-1", new SaveOverallDraftRequest(), admin),
                "AUTH.FORBIDDEN");
    }

    @Test
    void nonOwnerCannotReadOrModifyDraft() {
        UserPrincipal other = AnnotationTestFixtures.principal("annotator-2", Role.ANNOTATOR);

        assertCode(() -> service.getDraft("task-1", other), TaskErrorCodes.NOT_FOUND);
        assertCode(
                () -> service.saveOverall("task-1", new SaveOverallDraftRequest(), other),
                TaskErrorCodes.NOT_FOUND);
        verifyNoInteractions(draftRepository, submissionRepository, taskService, mongoTemplate);
    }

    @Test
    void nonOwnerCannotSubmitDraft() {
        UserPrincipal other = AnnotationTestFixtures.principal("annotator-2", Role.ANNOTATOR);

        assertCode(() -> service.submitReview("task-1", other), TaskErrorCodes.NOT_FOUND);
        verifyNoInteractions(draftRepository, submissionRepository, taskService, mongoTemplate);
    }

    @Test
    void nonEditingTaskCannotBeModified() {
        UserPrincipal owner = AnnotationTestFixtures.principal("annotator-1", Role.ANNOTATOR);
        when(taskRepository.findByTaskIdAndAnnotatorId("task-1", "annotator-1"))
                .thenReturn(Optional.of(AnnotationTestFixtures.task(TaskState.PENDING_REVIEW)));

        assertCode(
                () -> service.saveOverall("task-1", new SaveOverallDraftRequest(), owner),
                AnnotationErrorCodes.TASK_NOT_EDITABLE);
        verify(mongoTemplate, never()).upsert(any(), any(), any(Class.class));
    }

    @Test
    void completeDraftCreatesSingleFrozenSubmissionThenTransitionsTask() {
        TaskDocument annotating = AnnotationTestFixtures.task(TaskState.ANNOTATING);
        UserPrincipal owner = AnnotationTestFixtures.principal("annotator-1", Role.ANNOTATOR);
        when(taskRepository.findByTaskIdAndAnnotatorId("task-1", "annotator-1"))
                .thenReturn(Optional.of(annotating));
        when(draftRepository.findById("task-1")).thenReturn(Optional.of(completeDraft()));
        when(submissionRepository.existsByTaskIdAndSubmissionNo("task-1", 1)).thenReturn(false);
        when(submissionRepository.insert(any(TaskSubmissionDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(taskService.submitReview("task-1", "annotator-1"))
                .thenReturn(TaskDetailResponse.from(
                        AnnotationTestFixtures.task(TaskState.PENDING_REVIEW)));

        SubmitReviewResponse response = service.submitReview("task-1", owner);

        assertThat(response.taskState()).isEqualTo(TaskState.PENDING_REVIEW);
        ArgumentCaptor<TaskSubmissionDocument> captor =
                ArgumentCaptor.forClass(TaskSubmissionDocument.class);
        verify(submissionRepository).insert(captor.capture());
        assertThat(captor.getValue().getSubmissionNo()).isEqualTo(1);
        assertThat(captor.getValue().getOverallSnapshot().lawCategory()).isEqualTo("民事");
        assertThat(captor.getValue().getArticleSnapshots()).containsOnlyKeys("article-1", "article-2");
        verify(taskService).submitReview("task-1", "annotator-1");
    }

    @Test
    void incompleteSubmissionReturnsAllLocatorsWithoutChangingTask() {
        TaskDocument annotating = AnnotationTestFixtures.task(TaskState.ANNOTATING);
        UserPrincipal owner = AnnotationTestFixtures.principal("annotator-1", Role.ANNOTATOR);
        when(taskRepository.findByTaskIdAndAnnotatorId("task-1", "annotator-1"))
                .thenReturn(Optional.of(annotating));
        when(draftRepository.findById("task-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitReview("task-1", owner))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException api = (ApiException) error;
                    assertThat(api.getCode()).isEqualTo(AnnotationErrorCodes.SUBMISSION_INCOMPLETE);
                    assertThat(api.getLocators()).hasSize(6);
                });
        verifyNoInteractions(taskService);
        verify(submissionRepository, never()).insert(any(TaskSubmissionDocument.class));
    }

    @Test
    void repeatedSubmissionDoesNotCreateAnotherRecord() {
        UserPrincipal owner = AnnotationTestFixtures.principal("annotator-1", Role.ANNOTATOR);
        when(taskRepository.findByTaskIdAndAnnotatorId("task-1", "annotator-1"))
                .thenReturn(Optional.of(AnnotationTestFixtures.task(TaskState.PENDING_REVIEW)));

        assertCode(() -> service.submitReview("task-1", owner), TaskErrorCodes.ALREADY_SUBMITTED);
        verify(submissionRepository, never()).insert(any(TaskSubmissionDocument.class));
        verifyNoInteractions(taskService);
    }

    @Test
    void duplicateSubmissionIndexIsMappedToBusinessError() {
        UserPrincipal owner = AnnotationTestFixtures.principal("annotator-1", Role.ANNOTATOR);
        when(taskRepository.findByTaskIdAndAnnotatorId("task-1", "annotator-1"))
                .thenReturn(Optional.of(AnnotationTestFixtures.task(TaskState.ANNOTATING)));
        when(draftRepository.findById("task-1")).thenReturn(Optional.of(completeDraft()));
        when(submissionRepository.insert(any(TaskSubmissionDocument.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertCode(() -> service.submitReview("task-1", owner), TaskErrorCodes.ALREADY_SUBMITTED);
        verifyNoInteractions(taskService);
    }

    private static TaskDraftDocument completeDraft() {
        return new TaskDraftDocument(
                "task-1",
                new OverallDraftValues("民事", "合同", null, null),
                Map.of(
                        "article-1",
                        new ArticleDraftValues(ItemType.DEFINITION, "定义", null, null, null),
                        "article-2",
                        new ArticleDraftValues(ItemType.RIGHTS_DUTIES, "权利", null, null, null)),
                3,
                "annotator-1",
                AnnotationTestFixtures.NOW,
                AnnotationTestFixtures.NOW);
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
