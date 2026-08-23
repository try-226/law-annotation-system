package com.law.annotation.annotation;

import com.law.annotation.annotation.AnnotationFieldValidator.ValidationResult;
import com.law.annotation.annotation.dto.AnnotationDraftResponse;
import com.law.annotation.annotation.dto.AnnotationWorkbenchResponse;
import com.law.annotation.annotation.dto.ArticleDraftResponse;
import com.law.annotation.annotation.dto.SaveArticleDraftRequest;
import com.law.annotation.annotation.dto.SaveOverallDraftRequest;
import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.common.response.ErrorLocator;
import com.law.annotation.field.FieldConfigScope;
import com.law.annotation.field.FieldConfigSnapshot;
import com.law.annotation.field.FieldConfigSnapshotItem;
import com.law.annotation.field.FixedAnnotationField;
import com.law.annotation.task.TaskArticleSnapshot;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.task.TaskRepository;
import com.law.annotation.task.dto.TaskDetailResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AnnotationDraftService {

    private final TaskRepository taskRepository;
    private final AnnotationDraftRepository draftRepository;
    private final MongoTemplate mongoTemplate;

    public AnnotationDraftService(
            TaskRepository taskRepository,
            AnnotationDraftRepository draftRepository,
            MongoTemplate mongoTemplate) {
        this.taskRepository = taskRepository;
        this.draftRepository = draftRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public AnnotationWorkbenchResponse getWorkbench(
            String taskId,
            UserPrincipal currentUser) {
        TaskDocument task = requireOwnedTask(taskId, currentUser);
        validateTaskSnapshots(task);
        AnnotationDraftDocument draft = draftRepository.findById(task.getTaskId()).orElse(null);
        return new AnnotationWorkbenchResponse(
                TaskDetailResponse.from(task),
                toResponse(task, draft));
    }

    public AnnotationDraftResponse saveOverall(
            String taskId,
            SaveOverallDraftRequest request,
            UserPrincipal currentUser) {
        TaskDocument task = requireAnnotatingTask(taskId, currentUser);
        Objects.requireNonNull(request, "request must not be null");
        ValidationResult<OverallAnnotationFields> validation =
                AnnotationFieldValidator.validateOverall(request, "");
        throwIfInvalid(validation.errors());

        Instant now = Instant.now();
        AnnotationDraftDocument draft = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(task.getTaskId())),
                new Update()
                        .setOnInsert("createdAt", now)
                        .setOnInsert("articleFields", new LinkedHashMap<>())
                        .set("annotatorId", task.getAnnotatorId())
                        .set("overallFields", validation.value())
                        .set("updatedAt", now),
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                AnnotationDraftDocument.class);
        return toResponse(task, requireDraftResult(draft));
    }

    public AnnotationDraftResponse saveArticle(
            String taskId,
            String articleId,
            SaveArticleDraftRequest request,
            UserPrincipal currentUser) {
        TaskDocument task = requireAnnotatingTask(taskId, currentUser);
        String validArticleId = requireIdentifier(articleId, "articleId");
        requireTaskArticle(task, validArticleId);
        Objects.requireNonNull(request, "request must not be null");
        ValidationResult<ArticleAnnotationFields> validation =
                AnnotationFieldValidator.validateArticle(request, "");
        throwIfInvalid(validation.errors());

        Instant now = Instant.now();
        AnnotationDraftDocument draft = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(task.getTaskId())),
                new Update()
                        .setOnInsert("createdAt", now)
                        .set("annotatorId", task.getAnnotatorId())
                        .set("articleFields." + validArticleId, validation.value())
                        .set("updatedAt", now),
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                AnnotationDraftDocument.class);
        return toResponse(task, requireDraftResult(draft));
    }

    public TaskDetailResponse submitReview(
            String taskId,
            UserPrincipal currentUser) {
        TaskDocument task = requireAnnotatingTask(taskId, currentUser);
        AnnotationDraftDocument draft = draftRepository.findById(task.getTaskId()).orElse(null);
        List<ErrorLocator> errors = validateForSubmit(task, draft);
        throwIfInvalid(errors);

        Instant now = Instant.now();
        TaskDocument updated = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(task.getTaskId())
                        .and("taskType").is(TaskType.ORDINARY)
                        .and("taskState").is(TaskState.ANNOTATING)
                        .and("annotatorId").is(task.getAnnotatorId())),
                new Update()
                        .set("taskState", TaskState.PENDING_REVIEW)
                        .set("updatedAt", now),
                FindAndModifyOptions.options().returnNew(true),
                TaskDocument.class);
        if (updated != null) {
            return TaskDetailResponse.from(updated);
        }

        TaskDocument current = requireOwnedTask(task.getTaskId(), currentUser);
        throw invalidTaskState(current.getTaskState());
    }

    private AnnotationDraftResponse toResponse(
            TaskDocument task,
            AnnotationDraftDocument draft) {
        Map<String, Boolean> overallRequired = requiredByKey(task, FieldConfigScope.OVERALL);
        Map<String, Boolean> articleRequired = requiredByKey(task, FieldConfigScope.ARTICLE);
        OverallAnnotationFields overallFields = draft == null ? null : draft.getOverallFields();
        Map<String, ArticleAnnotationFields> storedArticles =
                draft == null ? Map.of() : draft.getArticleFields();
        List<ArticleDraftResponse> articleResponses = task.getContentVersionSnapshot().articles().stream()
                .map(article -> {
                    ArticleAnnotationFields fields = storedArticles.get(article.articleId());
                    return new ArticleDraftResponse(
                            article.articleId(),
                            fields,
                            requiredArticleErrors(fields, articleRequired, "").isEmpty());
                })
                .toList();
        int filledArticleCount = (int) articleResponses.stream()
                .filter(ArticleDraftResponse::filled)
                .count();
        return new AnnotationDraftResponse(
                task.getTaskId(),
                task.getAnnotatorId(),
                overallFields,
                requiredOverallErrors(overallFields, overallRequired, "").isEmpty(),
                articleResponses,
                filledArticleCount,
                articleResponses.size(),
                draft == null ? null : draft.getCreatedAt(),
                draft == null ? null : draft.getUpdatedAt());
    }

    private List<ErrorLocator> validateForSubmit(
            TaskDocument task,
            AnnotationDraftDocument draft) {
        Map<String, Boolean> overallRequired = requiredByKey(task, FieldConfigScope.OVERALL);
        Map<String, Boolean> articleRequired = requiredByKey(task, FieldConfigScope.ARTICLE);
        OverallAnnotationFields overall = draft == null ? null : draft.getOverallFields();
        Map<String, ArticleAnnotationFields> articles =
                draft == null ? Map.of() : draft.getArticleFields();
        List<ErrorLocator> errors = new ArrayList<>();

        ValidationResult<OverallAnnotationFields> overallValidation =
                AnnotationFieldValidator.validateStoredOverall(overall, "overall");
        errors.addAll(overallValidation.errors());
        errors.addAll(requiredOverallErrors(
                overallValidation.value(), overallRequired, "overall"));

        List<TaskArticleSnapshot> taskArticles = task.getContentVersionSnapshot().articles();
        for (int index = 0; index < taskArticles.size(); index++) {
            TaskArticleSnapshot taskArticle = taskArticles.get(index);
            String prefix = "article[" + (index + 1) + "]";
            ValidationResult<ArticleAnnotationFields> articleValidation =
                    AnnotationFieldValidator.validateStoredArticle(
                            articles.get(taskArticle.articleId()), prefix);
            errors.addAll(articleValidation.errors());
            errors.addAll(requiredArticleErrors(
                    articleValidation.value(), articleRequired, prefix));
        }
        return List.copyOf(errors);
    }

    private static List<ErrorLocator> requiredOverallErrors(
            OverallAnnotationFields fields,
            Map<String, Boolean> requiredByKey,
            String prefix) {
        List<ErrorLocator> errors = new ArrayList<>();
        addRequiredError(
                errors,
                requiredByKey,
                "lawCategory",
                fields == null ? null : fields.lawCategory(),
                prefix);
        addRequiredError(
                errors,
                requiredByKey,
                "overallKeywords",
                fields == null ? null : fields.overallKeywords(),
                prefix);
        addRequiredError(
                errors,
                requiredByKey,
                "summary",
                fields == null ? null : fields.summary(),
                prefix);
        addRequiredError(
                errors,
                requiredByKey,
                "overallNote",
                fields == null ? null : fields.overallNote(),
                prefix);
        return errors;
    }

    private static List<ErrorLocator> requiredArticleErrors(
            ArticleAnnotationFields fields,
            Map<String, Boolean> requiredByKey,
            String prefix) {
        List<ErrorLocator> errors = new ArrayList<>();
        addRequiredError(
                errors,
                requiredByKey,
                "itemType",
                fields == null ? null : fields.itemType(),
                prefix);
        addRequiredError(
                errors,
                requiredByKey,
                "keywords",
                fields == null ? null : fields.keywords(),
                prefix);
        addRequiredError(
                errors,
                requiredByKey,
                "subjects",
                fields == null ? null : fields.subjects(),
                prefix);
        addRequiredError(
                errors,
                requiredByKey,
                "legalLiability",
                fields == null ? null : fields.legalLiability(),
                prefix);
        addRequiredError(
                errors,
                requiredByKey,
                "annotationNote",
                fields == null ? null : fields.annotationNote(),
                prefix);
        return errors;
    }

    private static void addRequiredError(
            List<ErrorLocator> errors,
            Map<String, Boolean> requiredByKey,
            String fieldKey,
            Object value,
            String prefix) {
        if (Boolean.TRUE.equals(requiredByKey.get(fieldKey)) && isMissing(value)) {
            errors.add(new ErrorLocator(fieldPath(prefix, fieldKey), "required"));
        }
    }

    private Map<String, Boolean> requiredByKey(
            TaskDocument task,
            FieldConfigScope scope) {
        FieldConfigSnapshot snapshot = task.getFieldConfigSnapshot();
        if (snapshot == null) {
            throw snapshotInvalid();
        }
        List<FieldConfigSnapshotItem> items = scope == FieldConfigScope.OVERALL
                ? snapshot.overall()
                : snapshot.article();
        if (items == null) {
            throw snapshotInvalid();
        }
        Set<String> expectedKeys = java.util.Arrays.stream(FixedAnnotationField.values())
                .filter(field -> field.scope() == scope)
                .map(FixedAnnotationField::fieldKey)
                .collect(Collectors.toSet());
        LinkedHashMap<String, Boolean> requiredByKey = new LinkedHashMap<>();
        for (FieldConfigSnapshotItem item : items) {
            FixedAnnotationField field = item == null
                    ? null
                    : FixedAnnotationField.findByKey(item.fieldKey()).orElse(null);
            if (item == null
                    || field == null
                    || field.scope() != scope
                    || !field.configurable() && !item.required()
                    || !expectedKeys.contains(item.fieldKey())
                    || requiredByKey.putIfAbsent(item.fieldKey(), item.required()) != null) {
                throw snapshotInvalid();
            }
        }
        if (!requiredByKey.keySet().equals(expectedKeys)) {
            throw snapshotInvalid();
        }
        return Map.copyOf(requiredByKey);
    }

    private TaskDocument requireAnnotatingTask(
            String taskId,
            UserPrincipal currentUser) {
        TaskDocument task = requireOwnedTask(taskId, currentUser);
        validateTaskSnapshots(task);
        if (task.getTaskState() != TaskState.ANNOTATING) {
            throw invalidTaskState(task.getTaskState());
        }
        return task;
    }

    private TaskDocument requireOwnedTask(
            String taskId,
            UserPrincipal currentUser) {
        String validTaskId = requireIdentifier(taskId, "taskId");
        if (currentUser == null || currentUser.role() != Role.ANNOTATOR) {
            throw notTaskOwner();
        }
        TaskDocument task = taskRepository.findById(validTaskId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        AnnotationErrorCodes.TASK_NOT_FOUND,
                        "任务不存在"));
        if (!Objects.equals(task.getAnnotatorId(), currentUser.id())) {
            throw notTaskOwner();
        }
        return task;
    }

    private static void validateTaskSnapshots(TaskDocument task) {
        if (task.getTaskType() != TaskType.ORDINARY
                || task.getContentVersionSnapshot() == null
                || task.getContentVersionSnapshot().articles() == null
                || task.getContentVersionSnapshot().articles().isEmpty()
                || task.getFieldConfigSnapshot() == null) {
            throw snapshotInvalid();
        }
    }

    private static TaskArticleSnapshot requireTaskArticle(
            TaskDocument task,
            String articleId) {
        return task.getContentVersionSnapshot().articles().stream()
                .filter(article -> article.articleId().equals(articleId))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        AnnotationErrorCodes.ARTICLE_NOT_IN_TASK,
                        "法条不属于任务绑定的内容版本",
                        List.of(new ErrorLocator("articleId", "法条不在任务快照中"))));
    }

    private static void throwIfInvalid(List<ErrorLocator> errors) {
        if (!errors.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    AnnotationErrorCodes.VALIDATION_FAILED,
                    "标注字段校验失败",
                    errors);
        }
    }

    private static AnnotationDraftDocument requireDraftResult(
            AnnotationDraftDocument draft) {
        if (draft == null) {
            throw new IllegalStateException("标注草稿保存失败");
        }
        return draft;
    }

    private static ApiException invalidTaskState(TaskState state) {
        return new ApiException(
                HttpStatus.CONFLICT,
                AnnotationErrorCodes.INVALID_TASK_STATE,
                "当前任务状态不允许保存或提交标注：" + state);
    }

    private static ApiException notTaskOwner() {
        return new ApiException(
                HttpStatus.FORBIDDEN,
                AnnotationErrorCodes.NOT_TASK_OWNER,
                "只有任务标注员可以访问标注内容");
    }

    private static ApiException snapshotInvalid() {
        return new ApiException(
                HttpStatus.CONFLICT,
                AnnotationErrorCodes.SNAPSHOT_INVALID,
                "任务标注快照无效");
    }

    private static String requireIdentifier(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    AnnotationErrorCodes.VALIDATION_FAILED,
                    "请求参数校验失败",
                    List.of(new ErrorLocator(fieldName, "不能为空")));
        }
        return value.trim();
    }

    private static boolean isMissing(Object value) {
        return value == null || value instanceof String text && text.isBlank();
    }

    private static String fieldPath(String prefix, String fieldKey) {
        return prefix == null || prefix.isBlank() ? fieldKey : prefix + "." + fieldKey;
    }
}
