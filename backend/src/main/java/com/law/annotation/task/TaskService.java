package com.law.annotation.task;

import com.law.annotation.auth.AuthErrorCodes;
import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.common.response.ErrorLocator;
import com.law.annotation.common.response.PageResponse;
import com.law.annotation.field.FieldConfigService;
import com.law.annotation.field.FieldConfigSnapshot;
import com.law.annotation.law.LawDocument;
import com.law.annotation.law.LawOperationCoordinator;
import com.law.annotation.law.LawRepository;
import com.law.annotation.task.dto.TaskDetailResponse;
import com.law.annotation.task.dto.TaskListItemResponse;
import com.law.annotation.user.UserDocument;
import com.law.annotation.user.UserRepository;
import com.law.annotation.version.ContentVersionDocument;
import com.law.annotation.version.ContentVersionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TaskService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_TASK_NAME_LENGTH = 100;
    private static final String AUTO_TASK_NAME_SUFFIX = "普通标注任务";
    private static final List<TaskState> CANCELABLE_STATES = List.of(
            TaskState.PENDING_ANNOTATION,
            TaskState.ANNOTATING);

    private final TaskRepository taskRepository;
    private final LawRepository lawRepository;
    private final ContentVersionRepository contentVersionRepository;
    private final UserRepository userRepository;
    private final FieldConfigService fieldConfigService;
    private final MongoTemplate mongoTemplate;
    private final LawOperationCoordinator operationCoordinator;

    public TaskService(
            TaskRepository taskRepository,
            LawRepository lawRepository,
            ContentVersionRepository contentVersionRepository,
            UserRepository userRepository,
            FieldConfigService fieldConfigService,
            MongoTemplate mongoTemplate,
            LawOperationCoordinator operationCoordinator) {
        this.taskRepository = taskRepository;
        this.lawRepository = lawRepository;
        this.contentVersionRepository = contentVersionRepository;
        this.userRepository = userRepository;
        this.fieldConfigService = fieldConfigService;
        this.mongoTemplate = mongoTemplate;
        this.operationCoordinator = operationCoordinator;
    }

    public TaskDetailResponse createOrdinaryTask(
            String lawId,
            String annotatorId,
            String taskName,
            String remark,
            String createdBy) {
        String validLawId = requireIdentifier(lawId, "lawId");
        requireEligibleLaw(validLawId);
        return operationCoordinator.withVisibleLaw(
                validLawId,
                TaskService::activeTaskConflict,
                operationToken -> createOrdinaryTaskLocked(
                        validLawId,
                        annotatorId,
                        taskName,
                        remark,
                        createdBy,
                        operationToken));
    }

    private TaskDetailResponse createOrdinaryTaskLocked(
            String lawId,
            String annotatorId,
            String taskName,
            String remark,
            String createdBy,
            String operationToken) {
        String validLawId = requireIdentifier(lawId, "lawId");
        String validAnnotatorId = requireIdentifier(annotatorId, "annotatorId");
        String validCreator = requireIdentifier(createdBy, "createdBy");
        String validRemark = optionalText(remark, "remark", 500);

        LawDocument law = requireEligibleLaw(validLawId);
        String validTaskName = resolveTaskName(taskName, law.getName());
        if (taskRepository.existsByLawIdAndTaskStateIn(
                validLawId, TaskStateRules.UNFINISHED_STATES)) {
            throw activeTaskConflict();
        }
        ContentVersionDocument contentVersion = requireCurrentContentVersion(law);
        if (contentVersion.getSemanticArticlesSnapshot().isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    TaskErrorCodes.NO_VALID_ARTICLE,
                    "法律至少需要一条有效法条才能创建任务");
        }
        UserDocument annotator = requireEligibleAnnotator(validAnnotatorId);
        FieldConfigSnapshot fieldConfigSnapshot = fieldConfigService.getCurrentSnapshot();
        Instant now = Instant.now();
        TaskDocument task = new TaskDocument(
                UUID.randomUUID().toString(),
                TaskType.ORDINARY,
                TaskState.PENDING_ANNOTATION,
                law.getId(),
                annotator.getId(),
                annotator.getName(),
                validTaskName,
                validRemark,
                contentVersion.getId(),
                TaskContentVersionSnapshot.from(contentVersion),
                TaskLawBaseInfoSnapshot.from(law),
                law.getStructure().stream()
                        .map(TaskStructureNodeSnapshot::from)
                        .toList(),
                fieldConfigSnapshot,
                validCreator,
                null,
                null,
                null,
                null,
                now,
                now);
        operationCoordinator.renewVisibleLaw(
                validLawId,
                operationToken,
                TaskService::activeTaskConflict);
        try {
            return TaskDetailResponse.from(taskRepository.insert(task));
        } catch (DuplicateKeyException exception) {
            throw activeTaskConflict();
        }
    }

    public TaskDetailResponse start(String taskId, String actorId) {
        String validTaskId = requireIdentifier(taskId, "taskId");
        String validActorId = requireIdentifier(actorId, "actorId");
        Instant now = Instant.now();
        Query query = Query.query(Criteria.where("_id").is(validTaskId)
                .and("taskType").is(TaskType.ORDINARY)
                .and("taskState").is(TaskState.PENDING_ANNOTATION)
                .and("annotatorId").is(validActorId));
        TaskDocument updated = mongoTemplate.findAndModify(
                query,
                new Update()
                        .set("taskState", TaskState.ANNOTATING)
                        .set("updatedAt", now),
                FindAndModifyOptions.options().returnNew(true),
                TaskDocument.class);
        if (updated != null) {
            return TaskDetailResponse.from(updated);
        }

        TaskDocument current = requireTask(validTaskId);
        if (!Objects.equals(current.getAnnotatorId(), validActorId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    TaskErrorCodes.NOT_ASSIGNEE,
                    "只有任务标注员可以开始任务");
        }
        throw invalidTransition(current.getTaskState(), "开始");
    }

    public TaskDetailResponse submitReview(
            String taskId,
            String actorId,
            String submissionId) {
        String validTaskId = requireIdentifier(taskId, "taskId");
        String validActorId = requireIdentifier(actorId, "actorId");
        String validSubmissionId = requireIdentifier(submissionId, "submissionId");
        Instant now = Instant.now();
        Query query = Query.query(Criteria.where("_id").is(validTaskId)
                .and("taskType").is(TaskType.ORDINARY)
                .and("taskState").is(TaskState.ANNOTATING)
                .and("annotatorId").is(validActorId)
                .and("initialSubmissionId").is(null));
        TaskDocument updated = mongoTemplate.findAndModify(
                query,
                new Update()
                        .set("taskState", TaskState.PENDING_REVIEW)
                        .set("initialSubmissionId", validSubmissionId)
                        .set("updatedAt", now),
                FindAndModifyOptions.options().returnNew(true),
                TaskDocument.class);
        if (updated != null) {
            return TaskDetailResponse.from(updated);
        }

        TaskDocument current = requireTask(validTaskId);
        if (!Objects.equals(current.getAnnotatorId(), validActorId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    TaskErrorCodes.NOT_ASSIGNEE,
                    "只有任务标注员可以提交审核");
        }
        if (current.getTaskState() == TaskState.PENDING_REVIEW) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    TaskErrorCodes.ALREADY_SUBMITTED,
                    "任务已经提交审核");
        }
        throw invalidTransition(current.getTaskState(), "提交审核");
    }

    public TaskDetailResponse cancel(String taskId, String reason, UserPrincipal currentUser) {
        if (currentUser == null || currentUser.role() != Role.ADMIN) {
            throw forbidden();
        }
        String validTaskId = requireIdentifier(taskId, "taskId");
        String validCanceledBy = requireIdentifier(currentUser.id(), "currentUser.id");
        String validReason = requiredText(reason, "reason", 500);
        Instant now = Instant.now();
        Query query = Query.query(Criteria.where("_id").is(validTaskId)
                .and("taskType").is(TaskType.ORDINARY)
                .and("taskState").in(CANCELABLE_STATES));
        TaskDocument updated = mongoTemplate.findAndModify(
                query,
                new Update()
                        .set("taskState", TaskState.CANCELED)
                        .set("cancelReason", validReason)
                        .set("canceledBy", validCanceledBy)
                        .set("canceledAt", now)
                        .set("updatedAt", now),
                FindAndModifyOptions.options().returnNew(true),
                TaskDocument.class);
        if (updated != null) {
            return TaskDetailResponse.from(updated);
        }
        throw invalidTransition(requireTask(validTaskId).getTaskState(), "取消");
    }

    public PageResponse<TaskListItemResponse> list(
            String taskName,
            TaskType taskType,
            String lawId,
            String annotatorId,
            TaskState taskState,
            int page,
            int size,
            UserPrincipal currentUser) {
        String effectiveAnnotatorId = effectiveAnnotatorFilter(annotatorId, currentUser);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw validation("page/size", "page不能小于0，size须为1至100");
        }
        if (taskType != null && taskType != TaskType.ORDINARY) {
            throw validation("taskType", "PR08仅支持ORDINARY普通任务");
        }
        Criteria criteria = Criteria.where("taskType").is(TaskType.ORDINARY);
        if (lawId != null && !lawId.isBlank()) {
            criteria = criteria.and("lawId").is(requireIdentifier(lawId, "lawId"));
        }
        if (effectiveAnnotatorId != null && !effectiveAnnotatorId.isBlank()) {
            criteria = criteria.and("annotatorId").is(
                    requireIdentifier(effectiveAnnotatorId, "annotatorId"));
        }
        if (taskState != null) {
            criteria = criteria.and("taskState").is(taskState);
        }
        if (taskName != null && !taskName.isBlank()) {
            String search = requiredText(taskName, "taskName", 100);
            criteria = criteria.and("taskName").regex(
                    Pattern.compile(Pattern.quote(search), Pattern.CASE_INSENSITIVE));
        }
        Query countQuery = Query.query(criteria);
        long totalElements = mongoTemplate.count(countQuery, TaskDocument.class);
        List<TaskListItemResponse> items = mongoTemplate.find(
                        Query.query(criteria)
                                .with(Sort.by(
                                        Sort.Order.desc("createdAt"),
                                        Sort.Order.desc("_id")))
                                .skip((long) page * size)
                                .limit(size),
                        TaskDocument.class)
                .stream()
                .map(task -> new TaskListItemResponse(
                        task.getTaskId(),
                        task.getTaskName(),
                        task.getTaskType(),
                        task.getLawId(),
                        task.getLawBaseInfoSnapshot().name(),
                        task.getAnnotatorId(),
                        task.getAnnotatorNameSnapshot(),
                        task.getTaskState(),
                        task.getRemark(),
                        task.getCreatedAt()))
                .toList();
        int totalPages = totalElements == 0
                ? 0
                : (int) ((totalElements + size - 1) / size);
        return new PageResponse<>(items, page, size, totalElements, totalPages);
    }

    public TaskDetailResponse getDetail(String taskId, UserPrincipal currentUser) {
        String validTaskId = requireIdentifier(taskId, "taskId");
        if (currentUser == null) {
            throw forbidden();
        }
        if (currentUser.role() == Role.ADMIN) {
            return TaskDetailResponse.from(requireTask(validTaskId));
        }
        if (currentUser.role() == Role.ANNOTATOR) {
            return taskRepository.findByTaskIdAndAnnotatorId(validTaskId, currentUser.id())
                    .map(TaskDetailResponse::from)
                    .orElseThrow(TaskService::taskNotFound);
        }
        throw forbidden();
    }

    private LawDocument requireEligibleLaw(String lawId) {
        LawDocument law = lawRepository.findById(lawId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                TaskErrorCodes.LAW_NOT_FOUND,
                "法律不存在"));
        if (law.getDeletedAt() != null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    TaskErrorCodes.LAW_DELETED,
                    "已删除的法律不能创建任务");
        }
        if (law.getCurrentAnnotationVersionId() != null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    TaskErrorCodes.FORMAL_ANNOTATION_EXISTS,
                    "已有正式标注版本的法律不能创建普通任务");
        }
        if (law.isPendingRevision()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    TaskErrorCodes.FORMAL_ANNOTATION_EXISTS,
                    "待修订法律不能创建普通任务");
        }
        return law;
    }

    private ContentVersionDocument requireCurrentContentVersion(LawDocument law) {
        return contentVersionRepository.findById(law.getCurrentContentVersionId())
                .filter(version -> law.getId().equals(version.getLawId()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        TaskErrorCodes.CONTENT_VERSION_INVALID,
                        "法律当前内容版本无效"));
    }

    private UserDocument requireEligibleAnnotator(String annotatorId) {
        UserDocument annotator = userRepository.findById(annotatorId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        TaskErrorCodes.ANNOTATOR_NOT_FOUND,
                        "标注员不存在"));
        if (!annotator.isEnabled()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    TaskErrorCodes.ANNOTATOR_DISABLED,
                    "标注员已停用");
        }
        if (annotator.getRole() != Role.ANNOTATOR) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    TaskErrorCodes.ANNOTATOR_ROLE_INVALID,
                    "任务只能分配给标注员");
        }
        return annotator;
    }

    private TaskDocument requireTask(String taskId) {
        return taskRepository.findById(taskId).orElseThrow(TaskService::taskNotFound);
    }

    private static ApiException taskNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                TaskErrorCodes.NOT_FOUND,
                "任务不存在");
    }

    private static ApiException forbidden() {
        return new ApiException(
                HttpStatus.FORBIDDEN,
                AuthErrorCodes.FORBIDDEN,
                "无权执行此操作");
    }

    private static String requireIdentifier(String value, String path) {
        return requiredText(value, path, 100);
    }

    private static String optionalText(String value, String path, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requiredText(value, path, maxLength);
    }

    private static String resolveTaskName(String taskName, String lawName) {
        if (taskName != null && !taskName.isBlank()) {
            return requiredText(taskName, "taskName", MAX_TASK_NAME_LENGTH);
        }
        String validLawName = lawName == null ? "" : lawName.trim();
        if (validLawName.isBlank()) {
            return AUTO_TASK_NAME_SUFFIX;
        }
        int suffixLength = AUTO_TASK_NAME_SUFFIX.codePointCount(
                0, AUTO_TASK_NAME_SUFFIX.length());
        int maxLawNameLength = MAX_TASK_NAME_LENGTH - suffixLength;
        return truncateToCodePoints(validLawName, maxLawNameLength)
                + AUTO_TASK_NAME_SUFFIX;
    }

    private static String truncateToCodePoints(String value, int maxLength) {
        int codePointCount = value.codePointCount(0, value.length());
        if (codePointCount <= maxLength) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxLength));
    }

    private static String effectiveAnnotatorFilter(
            String requestedAnnotatorId,
            UserPrincipal currentUser) {
        if (currentUser == null) {
            throw forbidden();
        }
        if (currentUser.role() == Role.ADMIN) {
            return requestedAnnotatorId;
        }
        if (currentUser.role() == Role.ANNOTATOR) {
            return currentUser.id();
        }
        throw forbidden();
    }

    private static String requiredText(String value, String path, int maxLength) {
        if (value == null) {
            throw validation(path, "不能为空");
        }
        String trimmed = value.trim();
        int length = trimmed.codePointCount(0, trimmed.length());
        boolean containsControl = trimmed.codePoints().anyMatch(Character::isISOControl);
        if (length < 1 || length > maxLength || containsControl) {
            throw validation(path, "trim后须为1至" + maxLength + "个字符且不得包含控制字符");
        }
        return trimmed;
    }

    private static ApiException activeTaskConflict() {
        return new ApiException(
                HttpStatus.CONFLICT,
                TaskErrorCodes.TASK_ALREADY_EXISTS,
                "该法律已存在未结束任务");
    }

    private static ApiException invalidTransition(TaskState currentState, String action) {
        return new ApiException(
                HttpStatus.CONFLICT,
                TaskErrorCodes.INVALID_STATE_TRANSITION,
                "任务当前状态不允许" + action + "：" + currentState);
    }

    private static ApiException validation(String path, String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "COMMON.VALIDATION_FAILED",
                "请求参数校验失败",
                List.of(new ErrorLocator(path, message)));
    }
}
