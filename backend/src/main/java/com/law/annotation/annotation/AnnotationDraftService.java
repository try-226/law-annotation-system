package com.law.annotation.annotation;

import com.law.annotation.annotation.dto.AnnotationProgressResponse;
import com.law.annotation.annotation.dto.EditableScopeResponse;
import com.law.annotation.annotation.dto.SaveArticleDraftRequest;
import com.law.annotation.annotation.dto.SaveOverallDraftRequest;
import com.law.annotation.annotation.dto.SubmitReviewResponse;
import com.law.annotation.annotation.dto.TaskDraftResponse;
import com.law.annotation.auth.AuthErrorCodes;
import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.common.response.ErrorLocator;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.task.TaskErrorCodes;
import com.law.annotation.task.TaskRepository;
import com.law.annotation.task.TaskService;
import com.law.annotation.task.dto.TaskDetailResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AnnotationDraftService {

    private static final int INITIAL_SUBMISSION_NO = 1;

    private final TaskRepository taskRepository;
    private final TaskDraftRepository draftRepository;
    private final TaskSubmissionRepository submissionRepository;
    private final TaskService taskService;
    private final MongoTemplate mongoTemplate;

    public AnnotationDraftService(
            TaskRepository taskRepository,
            TaskDraftRepository draftRepository,
            TaskSubmissionRepository submissionRepository,
            TaskService taskService,
            MongoTemplate mongoTemplate) {
        this.taskRepository = taskRepository;
        this.draftRepository = draftRepository;
        this.submissionRepository = submissionRepository;
        this.taskService = taskService;
        this.mongoTemplate = mongoTemplate;
    }

    public TaskDraftResponse getDraft(String taskId, UserPrincipal currentUser) {
        TaskDocument task = requireReadableTask(taskId, currentUser);
        TaskDraftDocument draft = draftRepository.findById(task.getTaskId()).orElse(null);
        return response(task, draft, currentUser);
    }

    public TaskDraftResponse saveOverall(
            String taskId,
            SaveOverallDraftRequest request,
            UserPrincipal currentUser) {
        TaskDocument task = requireEditableOwnerTask(taskId, currentUser);
        OverallDraftValues values = AnnotationDraftRules.normalizeOverall(request);
        Instant now = Instant.now();
        mongoTemplate.upsert(
                Query.query(Criteria.where("_id").is(task.getTaskId())),
                new Update()
                        .setOnInsert("_id", task.getTaskId())
                        .setOnInsert("createdAt", now)
                        .set("overallDraft", values)
                        .set("updatedBy", currentUser.id())
                        .set("updatedAt", now)
                        .inc("revision", 1),
                TaskDraftDocument.class);
        return response(task, requireDraft(task.getTaskId()), currentUser);
    }

    public TaskDraftResponse saveArticle(
            String taskId,
            String articleId,
            SaveArticleDraftRequest request,
            UserPrincipal currentUser) {
        TaskDocument task = requireEditableOwnerTask(taskId, currentUser);
        String validArticleId = requireIdentifier(articleId, "articleId");
        if (!AnnotationDraftRules.containsArticle(task, validArticleId)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    AnnotationErrorCodes.ARTICLE_NOT_FOUND,
                    "任务快照中不存在该法条");
        }
        ArticleDraftValues values = AnnotationDraftRules.normalizeArticle(request);
        Instant now = Instant.now();
        mongoTemplate.upsert(
                Query.query(Criteria.where("_id").is(task.getTaskId())),
                new Update()
                        .setOnInsert("_id", task.getTaskId())
                        .setOnInsert("createdAt", now)
                        .set("perArticleDrafts." + validArticleId, values)
                        .set("updatedBy", currentUser.id())
                        .set("updatedAt", now)
                        .inc("revision", 1),
                TaskDraftDocument.class);
        return response(task, requireDraft(task.getTaskId()), currentUser);
    }

    public TaskDraftResponse clearOverall(String taskId, UserPrincipal currentUser) {
        TaskDocument task = requireEditableOwnerTask(taskId, currentUser);
        clearField(task, "overallDraft", currentUser.id());
        return response(task, draftRepository.findById(task.getTaskId()).orElse(null), currentUser);
    }

    public TaskDraftResponse clearArticle(
            String taskId,
            String articleId,
            UserPrincipal currentUser) {
        TaskDocument task = requireEditableOwnerTask(taskId, currentUser);
        String validArticleId = requireIdentifier(articleId, "articleId");
        if (!AnnotationDraftRules.containsArticle(task, validArticleId)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    AnnotationErrorCodes.ARTICLE_NOT_FOUND,
                    "任务快照中不存在该法条");
        }
        clearField(task, "perArticleDrafts." + validArticleId, currentUser.id());
        return response(task, draftRepository.findById(task.getTaskId()).orElse(null), currentUser);
    }

    public SubmitReviewResponse submitReview(String taskId, UserPrincipal currentUser) {
        TaskDocument task = requireOwnerTask(taskId, currentUser);
        if (task.getTaskType() != TaskType.ORDINARY) {
            throw notEditable();
        }
        if (task.getTaskState() == TaskState.PENDING_REVIEW
                || submissionRepository.existsByTaskIdAndSubmissionNo(
                        task.getTaskId(), INITIAL_SUBMISSION_NO)) {
            throw alreadySubmitted();
        }
        if (task.getTaskState() != TaskState.ANNOTATING) {
            throw notEditable();
        }

        TaskDraftDocument draft = draftRepository.findById(task.getTaskId()).orElse(null);
        List<ErrorLocator> missing = AnnotationDraftRules.missingRequired(task, draft);
        if (!missing.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    AnnotationErrorCodes.SUBMISSION_INCOMPLETE,
                    "整体信息或法条标注尚未完成",
                    missing);
        }

        Instant submittedAt = Instant.now();
        String submissionId = UUID.randomUUID().toString();
        TaskSubmissionDocument submission = new TaskSubmissionDocument(
                submissionId,
                task.getTaskId(),
                INITIAL_SUBMISSION_NO,
                draft.getRevision(),
                draft.getOverallDraft(),
                orderedArticleSnapshots(task, draft),
                currentUser.id(),
                submittedAt);
        try {
            submissionRepository.insert(submission);
        } catch (DuplicateKeyException exception) {
            throw alreadySubmitted();
        }

        try {
            TaskDetailResponse updated = taskService.submitReview(
                    task.getTaskId(), currentUser.id());
            return new SubmitReviewResponse(
                    task.getTaskId(),
                    submissionId,
                    updated.taskState(),
                    submittedAt);
        } catch (RuntimeException exception) {
            submissionRepository.deleteById(submissionId);
            throw exception;
        }
    }

    private void clearField(TaskDocument task, String field, String actorId) {
        if (draftRepository.existsById(task.getTaskId())) {
            Instant now = Instant.now();
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(task.getTaskId())),
                    new Update()
                            .unset(field)
                            .set("updatedBy", actorId)
                            .set("updatedAt", now)
                            .inc("revision", 1),
                    TaskDraftDocument.class);
        }
    }

    private TaskDraftResponse response(
            TaskDocument task,
            TaskDraftDocument draft,
            UserPrincipal currentUser) {
        Map<String, ArticleDraftValues> articleDrafts = draft == null
                ? Map.of()
                : draft.getPerArticleDrafts();
        AnnotationProgressResponse progress = AnnotationDraftRules.progress(task, draft);
        boolean ownerCanEdit = currentUser != null
                && currentUser.role() == Role.ANNOTATOR
                && currentUser.id().equals(task.getAnnotatorId())
                && task.getTaskType() == TaskType.ORDINARY
                && task.getTaskState() == TaskState.ANNOTATING;
        List<String> editableArticleIds = ownerCanEdit
                ? task.getContentVersionSnapshot().articles().stream()
                        .map(article -> article.articleId())
                        .toList()
                : List.of();
        return new TaskDraftResponse(
                task.getTaskId(),
                task.getTaskState(),
                draft == null ? null : draft.getOverallDraft(),
                articleDrafts,
                new EditableScopeResponse(ownerCanEdit, editableArticleIds),
                progress,
                draft == null ? 0 : draft.getRevision(),
                draft == null ? null : draft.getUpdatedAt());
    }

    private TaskDocument requireReadableTask(String taskId, UserPrincipal currentUser) {
        String validTaskId = requireIdentifier(taskId, "taskId");
        if (currentUser == null) {
            throw forbidden();
        }
        if (currentUser.role() == Role.ADMIN) {
            return taskRepository.findById(validTaskId).orElseThrow(AnnotationDraftService::taskNotFound);
        }
        if (currentUser.role() == Role.ANNOTATOR) {
            return taskRepository.findByTaskIdAndAnnotatorId(validTaskId, currentUser.id())
                    .orElseThrow(AnnotationDraftService::taskNotFound);
        }
        throw forbidden();
    }

    private TaskDocument requireOwnerTask(String taskId, UserPrincipal currentUser) {
        if (currentUser == null || currentUser.role() != Role.ANNOTATOR) {
            throw forbidden();
        }
        String validTaskId = requireIdentifier(taskId, "taskId");
        return taskRepository.findByTaskIdAndAnnotatorId(validTaskId, currentUser.id())
                .orElseThrow(AnnotationDraftService::taskNotFound);
    }

    private TaskDocument requireEditableOwnerTask(String taskId, UserPrincipal currentUser) {
        TaskDocument task = requireOwnerTask(taskId, currentUser);
        if (task.getTaskType() != TaskType.ORDINARY
                || task.getTaskState() != TaskState.ANNOTATING) {
            throw notEditable();
        }
        return task;
    }

    private TaskDraftDocument requireDraft(String taskId) {
        return draftRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("草稿写入后未找到"));
    }

    private static Map<String, ArticleDraftValues> orderedArticleSnapshots(
            TaskDocument task,
            TaskDraftDocument draft) {
        Map<String, ArticleDraftValues> ordered = new LinkedHashMap<>();
        task.getContentVersionSnapshot().articles().forEach(article -> ordered.put(
                article.articleId(),
                draft.getPerArticleDrafts().get(article.articleId())));
        return ordered;
    }

    private static String requireIdentifier(String value, String path) {
        if (value == null || value.isBlank()) {
            throw validation(path, "不能为空");
        }
        String normalized = value.trim();
        int length = normalized.codePointCount(0, normalized.length());
        boolean containsControl = normalized.codePoints().anyMatch(Character::isISOControl);
        if (length > 100 || containsControl) {
            throw validation(path, "须为1至100个字符且不得包含控制字符");
        }
        return normalized;
    }

    private static ApiException taskNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, TaskErrorCodes.NOT_FOUND, "任务不存在");
    }

    private static ApiException notEditable() {
        return new ApiException(
                HttpStatus.CONFLICT,
                AnnotationErrorCodes.TASK_NOT_EDITABLE,
                "任务当前状态不允许修改或提交标注");
    }

    private static ApiException alreadySubmitted() {
        return new ApiException(
                HttpStatus.CONFLICT,
                TaskErrorCodes.ALREADY_SUBMITTED,
                "任务已经提交审核");
    }

    private static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, AuthErrorCodes.FORBIDDEN, "无权执行此操作");
    }

    private static ApiException validation(String path, String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "COMMON.VALIDATION_FAILED",
                "请求参数校验失败",
                List.of(new ErrorLocator(path, message)));
    }
}
