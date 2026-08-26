package com.law.annotation.review;

import com.law.annotation.annotation.TaskSubmissionDocument;
import com.law.annotation.annotation.TaskSubmissionRepository;
import com.law.annotation.auth.AuthErrorCodes;
import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.ReviewItemState;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.common.response.ErrorLocator;
import com.law.annotation.law.LawDocument;
import com.law.annotation.law.PendingChangeSet;
import com.law.annotation.revision.RevisionMode;
import com.law.annotation.review.dto.ReviewDetailResponse;
import com.law.annotation.review.dto.ReviewItemResponse;
import com.law.annotation.review.dto.ReviewProgressResponse;
import com.law.annotation.review.dto.ReviewSubmissionSnapshotResponse;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.task.TaskErrorCodes;
import com.law.annotation.task.TaskRepository;
import com.law.annotation.version.AnnotationVersionDocument;
import com.law.annotation.version.AnnotationVersionRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ReviewService {

    private static final int MAX_ANNOTATION_VERSION_INSERT_ATTEMPTS = 5;

    private final TaskRepository taskRepository;
    private final TaskSubmissionRepository submissionRepository;
    private final ReviewRoundRepository reviewRoundRepository;
    private final AnnotationVersionRepository annotationVersionRepository;
    private final MongoTemplate mongoTemplate;

    public ReviewService(
            TaskRepository taskRepository,
            TaskSubmissionRepository submissionRepository,
            ReviewRoundRepository reviewRoundRepository,
            AnnotationVersionRepository annotationVersionRepository,
            MongoTemplate mongoTemplate) {
        this.taskRepository = taskRepository;
        this.submissionRepository = submissionRepository;
        this.reviewRoundRepository = reviewRoundRepository;
        this.annotationVersionRepository = annotationVersionRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public ReviewDetailResponse start(String taskId, UserPrincipal currentUser) {
        requireAdmin(currentUser);
        TaskDocument task = requireTask(taskId);
        ReviewRoundType roundType = roundTypeFor(task.getTaskState());
        String sourceSubmissionId = task.getCurrentSubmissionId() == null
                ? task.getInitialSubmissionId()
                : task.getCurrentSubmissionId();
        TaskSubmissionDocument source = requireSubmission(task, sourceSubmissionId);

        ReviewRoundDocument existing = reviewRoundRepository
                .findByTaskIdAndSourceSubmissionId(task.getTaskId(), source.getSubmissionId())
                .orElse(null);
        if (existing != null) {
            return resumeStart(task, existing, currentUser);
        }

        String previousSubmissionId = null;
        List<ReviewItemLocator> scope;
        if (roundType == ReviewRoundType.INITIAL_REVIEW) {
            scope = initialScope(task);
        } else {
            ReviewRoundDocument previous = requirePreviousRejectedRound(task, source);
            previousSubmissionId = previous.getSourceSubmissionId();
            scope = normalizedScope(task, source.getModifiedScope());
            if (scope.isEmpty()) {
                throw sourceInvalid("复审提交未记录修改范围");
            }
        }

        Map<String, ReviewItemState> states = new LinkedHashMap<>();
        scope.forEach(locator -> states.put(locator.storageKey(), ReviewItemState.UNREVIEWED));
        Instant now = Instant.now();
        ReviewRoundDocument candidate = new ReviewRoundDocument(
                UUID.randomUUID().toString(),
                task.getTaskId(),
                task.getLawId(),
                source.getSubmissionNo(),
                roundType,
                source.getSubmissionId(),
                previousSubmissionId,
                currentUser.id(),
                scope,
                states,
                Map.of(),
                scope.size(),
                0,
                scope.size(),
                0,
                null,
                null,
                null,
                now,
                now,
                null);
        ReviewRoundDocument round;
        try {
            round = reviewRoundRepository.insert(candidate);
        } catch (DuplicateKeyException exception) {
            round = reviewRoundRepository.findByTaskIdAndSourceSubmissionId(
                            task.getTaskId(), source.getSubmissionId())
                    .orElseThrow(() -> exception);
        }
        return resumeStart(task, round, currentUser);
    }

    public ReviewDetailResponse getReview(String taskId, UserPrincipal currentUser) {
        requireAdmin(currentUser);
        TaskDocument task = requireTask(taskId);
        if (task.getCurrentReviewRoundId() == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ReviewErrorCodes.NOT_STARTED,
                    "当前审核轮次尚未开始");
        }
        ReviewRoundDocument round = reviewRoundRepository
                .findById(task.getCurrentReviewRoundId())
                .filter(value -> task.getTaskId().equals(value.getTaskId()))
                .orElseThrow(ReviewService::reviewNotFound);
        return toResponse(task, round, currentUser);
    }

    public ReviewDetailResponse check(
            String taskId,
            String roundId,
            ReviewItemLocator locator,
            UserPrincipal currentUser) {
        ReviewContext context = requireWritableContext(taskId, roundId, locator, currentUser);
        ReviewItemState currentState = context.round().getItemStates().get(locator.storageKey());
        if (currentState == null) {
            throw itemNotInScope();
        }
        if (currentState == ReviewItemState.CHECKED) {
            return toResponse(context.task(), context.round(), currentUser);
        }
        Update update = new Update()
                .set(itemStatePath(locator), ReviewItemState.CHECKED)
                .unset(issuePath(locator));
        if (currentState == ReviewItemState.UNREVIEWED) {
            update.inc("reviewedCount", 1).inc("unreviewedCount", -1);
        } else {
            update.inc("needsChangeCount", -1);
        }
        ReviewRoundDocument updated = updateItem(context, locator, currentState, update);
        return toResponse(context.task(), updated, currentUser);
    }

    public ReviewDetailResponse issue(
            String taskId,
            String roundId,
            ReviewItemLocator locator,
            String reason,
            UserPrincipal currentUser) {
        String validReason = requireReason(reason);
        ReviewContext context = requireWritableContext(taskId, roundId, locator, currentUser);
        ReviewItemState currentState = context.round().getItemStates().get(locator.storageKey());
        Instant now = Instant.now();
        ReviewIssue issue = new ReviewIssue(
                context.round().getReviewRoundId(),
                context.task().getTaskId(),
                locator.type(),
                locator.articleId(),
                validReason,
                now);
        if (currentState == null) {
            if (context.round().getRoundType() != ReviewRoundType.REREVIEW) {
                throw itemNotInScope();
            }
            Query addQuery = baseWritableRoundQuery(context)
                    .addCriteria(Criteria.where(itemStatePath(locator)).exists(false));
            ReviewRoundDocument updated = mongoTemplate.findAndModify(
                    addQuery,
                    new Update()
                            .addToSet("requiredScope", locator)
                            .set(itemStatePath(locator), ReviewItemState.NEEDS_CHANGE)
                            .set(issuePath(locator), issue)
                            .inc("totalCount", 1)
                            .inc("reviewedCount", 1)
                            .inc("needsChangeCount", 1),
                    FindAndModifyOptions.options().returnNew(true),
                    ReviewRoundDocument.class);
            if (updated == null) {
                throw writeConflict();
            }
            return toResponse(context.task(), updated, currentUser);
        }

        Update update = new Update()
                .set(itemStatePath(locator), ReviewItemState.NEEDS_CHANGE)
                .set(issuePath(locator), issue);
        if (currentState == ReviewItemState.UNREVIEWED) {
            update.inc("reviewedCount", 1)
                    .inc("unreviewedCount", -1)
                    .inc("needsChangeCount", 1);
        } else if (currentState == ReviewItemState.CHECKED) {
            update.inc("needsChangeCount", 1);
        }
        ReviewRoundDocument updated = updateItem(context, locator, currentState, update);
        return toResponse(context.task(), updated, currentUser);
    }

    public ReviewDetailResponse complete(
            String taskId,
            String roundId,
            UserPrincipal currentUser) {
        requireAdmin(currentUser);
        TaskDocument task = requireTask(taskId);
        ReviewRoundDocument round = requireRound(task, roundId);
        requireReviewer(round, currentUser);
        if (round.getCompletedAt() != null) {
            throw alreadyCompleted();
        }

        if (round.getCompletionStartedAt() == null) {
            if (task.getTaskState() != expectedPendingState(round)
                    || !Objects.equals(task.getCurrentReviewRoundId(), round.getReviewRoundId())
                    || !Objects.equals(task.getCurrentSubmissionId(),
                            round.getSourceSubmissionId())) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        ReviewErrorCodes.INVALID_TASK_STATE,
                        "任务状态已变化，旧审核请求不能完成");
            }
            round = claimCompletionIntent(task, round, currentUser);
        }

        resumeCompletion(task, round);
        TaskDocument completedTask = requireTask(task.getTaskId());
        ReviewRoundDocument completedRound = requireRound(completedTask, round.getReviewRoundId());
        return toResponse(completedTask, completedRound, currentUser);
    }

    private ReviewRoundDocument claimCompletionIntent(
            TaskDocument task,
            ReviewRoundDocument initialRound,
            UserPrincipal currentUser) {
        ReviewRoundDocument round = initialRound;
        for (int attempt = 0; attempt < 5; attempt++) {
            if (round.getCompletedAt() != null) {
                throw alreadyCompleted();
            }
            if (round.getCompletionStartedAt() != null) {
                return round;
            }
            if (round.getUnreviewedCount() != 0) {
                throw incomplete(round.getUnreviewedCount());
            }
            ReviewRoundOutcome outcome = round.getNeedsChangeCount() == 0
                    ? ReviewRoundOutcome.APPROVED
                    : ReviewRoundOutcome.PARTIALLY_REJECTED;
            ReviewRoundDocument claimed = mongoTemplate.findAndModify(
                    Query.query(Criteria.where("_id").is(round.getReviewRoundId())
                            .and("reviewerId").is(currentUser.id())
                            .and("completedAt").is(null)
                            .and("completionStartedAt").is(null)
                            .and("unreviewedCount").is(0)
                            .and("needsChangeCount").is(round.getNeedsChangeCount())),
                    new Update()
                            .set("completionStartedAt", Instant.now())
                            .set("completionOutcome", outcome),
                    FindAndModifyOptions.options().returnNew(true),
                    ReviewRoundDocument.class);
            if (claimed != null) {
                return claimed;
            }
            round = requireRound(task, round.getReviewRoundId());
        }
        throw writeConflict();
    }

    public ReviewRoundDocument requireRejectedRoundForEditing(TaskDocument task) {
        if (task.getTaskState() != TaskState.PARTIALLY_REJECTED
                || task.getCurrentReviewRoundId() == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ReviewErrorCodes.INVALID_TASK_STATE,
                    "任务当前不处于部分驳回修改状态");
        }
        ReviewRoundDocument round = requireRound(task, task.getCurrentReviewRoundId());
        if (round.getCompletedAt() == null
                || round.getCompletionOutcome() != ReviewRoundOutcome.PARTIALLY_REJECTED) {
            throw sourceInvalid("当前驳回审核轮次无效");
        }
        return round;
    }

    private ReviewDetailResponse resumeStart(
            TaskDocument task,
            ReviewRoundDocument round,
            UserPrincipal currentUser) {
        if (!Objects.equals(round.getReviewerId(), currentUser.id())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ReviewErrorCodes.ALREADY_ASSIGNED,
                    "该审核轮次已由其他管理员领取");
        }
        TaskState expectedState = expectedPendingState(round);
        Criteria taskCriteria = new Criteria().andOperator(
                Criteria.where("_id").is(task.getTaskId()),
                Criteria.where("taskState").is(expectedState),
                new Criteria().orOperator(
                        Criteria.where("currentSubmissionId").is(null),
                        Criteria.where("currentSubmissionId")
                                .is(round.getSourceSubmissionId())));
        mongoTemplate.updateFirst(
                Query.query(taskCriteria),
                new Update()
                        .set("currentSubmissionId", round.getSourceSubmissionId())
                        .set("currentReviewRoundId", round.getReviewRoundId())
                        .set("updatedAt", Instant.now()),
                TaskDocument.class);
        TaskDocument refreshed = requireTask(task.getTaskId());
        if (!Objects.equals(refreshed.getCurrentReviewRoundId(), round.getReviewRoundId())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ReviewErrorCodes.INVALID_TASK_STATE,
                    "任务状态已变化，无法开始审核");
        }
        return toResponse(refreshed, round, currentUser);
    }

    private void resumeCompletion(TaskDocument originalTask, ReviewRoundDocument round) {
        ReviewRoundOutcome outcome = round.getCompletionOutcome();
        if (outcome == null) {
            throw sourceInvalid("审核完成意图缺少结果");
        }
        if (outcome == ReviewRoundOutcome.APPROVED) {
            ApprovedCompletionContext context = validateApprovedCompletionPreconditions(
                    originalTask,
                    round);
            AnnotationVersionDocument version = ensureAnnotationVersion(context);
            ensureRoundAnnotationVersion(context.round(), version);
            ensureLawAnnotationReference(context.task(), version);
            ensureTaskApproved(context.task(), context.round(), version);
        } else {
            ensureTaskRejected(originalTask, round);
        }
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(round.getReviewRoundId())
                        .and("completionStartedAt").is(round.getCompletionStartedAt())
                        .and("completionOutcome").is(outcome)
                        .and("completedAt").is(null)),
                new Update().set("completedAt", Instant.now()),
                ReviewRoundDocument.class);
        ReviewRoundDocument current = reviewRoundRepository.findById(round.getReviewRoundId())
                .orElseThrow(ReviewService::reviewNotFound);
        if (current.getCompletedAt() == null) {
            throw writeConflict();
        }
    }

    private ApprovedCompletionContext validateApprovedCompletionPreconditions(
            TaskDocument originalTask,
            ReviewRoundDocument completionIntent) {
        TaskDocument task = requireTask(originalTask.getTaskId());
        ReviewRoundDocument round = requireRound(task, completionIntent.getReviewRoundId());
        if (round.getCompletedAt() != null) {
            throw alreadyCompleted();
        }
        if (round.getCompletionOutcome() != ReviewRoundOutcome.APPROVED
                || round.getCompletionStartedAt() == null
                || !Objects.equals(
                        round.getCompletionStartedAt(),
                        completionIntent.getCompletionStartedAt())
                || !Objects.equals(
                        round.getSourceSubmissionId(),
                        completionIntent.getSourceSubmissionId())
                || round.getRoundNo() != completionIntent.getRoundNo()
                || round.getRoundType() != completionIntent.getRoundType()
                || !Objects.equals(round.getReviewerId(), completionIntent.getReviewerId())
                || round.getUnreviewedCount() != 0
                || round.getNeedsChangeCount() != 0) {
            throw completionConflict("审核完成意图已变化，无法完成审核");
        }
        TaskState expectedState = expectedPendingState(round);
        if ((task.getTaskState() != expectedState && task.getTaskState() != TaskState.APPROVED)
                || !Objects.equals(task.getLawId(), round.getLawId())
                || !Objects.equals(task.getCurrentReviewRoundId(), round.getReviewRoundId())
                || !Objects.equals(task.getCurrentSubmissionId(), round.getSourceSubmissionId())) {
            throw completionConflict("任务、当前审核轮次或当前冻结提交已变化，无法完成审核");
        }

        TaskSubmissionDocument submission = requireSubmission(
                task,
                round.getSourceSubmissionId());
        if (submission.getSubmissionNo() != round.getRoundNo()) {
            throw completionConflict("当前审核轮次与冻结提交序号不一致，无法完成审核");
        }
        validateFrozenSubmission(task, submission);

        LawDocument law = mongoTemplate.findById(task.getLawId(), LawDocument.class);
        if (law == null
                || !Objects.equals(
                        law.getCurrentContentVersionId(),
                        task.getContentVersionId())) {
            throw completionConflict("法律当前内容版本与任务内容版本不一致，无法完成审核");
        }

        AnnotationVersionDocument existing = annotationVersionRepository
                .findBySourceTaskId(task.getTaskId())
                .orElse(null);
        if (existing != null) {
            validateExistingAnnotationVersion(existing, task, round, submission);
        }
        if (round.getAnnotationVersionId() != null
                && (existing == null
                        || !Objects.equals(round.getAnnotationVersionId(), existing.getId()))) {
            throw completionConflict("审核轮次的正式标注引用与完成意图不一致");
        }
        if (task.getTaskType() == TaskType.REVISION) {
            validateRevisionLawBasis(law, task, existing);
        } else if (law.getCurrentAnnotationVersionId() != null
                && (existing == null
                        || !Objects.equals(
                                law.getCurrentAnnotationVersionId(),
                                existing.getId()))) {
            throw completionConflict("法律当前正式标注引用与完成意图不一致");
        }
        if (task.getTaskState() == TaskState.APPROVED) {
            if (existing == null
                    || !Objects.equals(task.getApprovedAnnotationVersionId(), existing.getId())) {
                throw completionConflict("任务已通过但正式标注引用与完成意图不一致");
            }
        } else if (task.getApprovedAnnotationVersionId() != null) {
            throw completionConflict("待审核任务不应存在正式标注引用");
        }

        return new ApprovedCompletionContext(task, round, submission, existing);
    }

    private AnnotationVersionDocument ensureAnnotationVersion(
            ApprovedCompletionContext context) {
        TaskDocument task = context.task();
        ReviewRoundDocument round = context.round();
        TaskSubmissionDocument submission = context.submission();
        if (context.existingVersion() != null) {
            return context.existingVersion();
        }
        for (int attempt = 0; attempt < MAX_ANNOTATION_VERSION_INSERT_ATTEMPTS; attempt++) {
            int nextSeq = annotationVersionRepository.findTopByLawIdOrderBySeqDesc(task.getLawId())
                    .map(version -> version.getSeq() + 1)
                    .orElse(1);
            AnnotationVersionDocument candidate = new AnnotationVersionDocument(
                    UUID.randomUUID().toString(),
                    task.getLawId(),
                    nextSeq,
                    task.getContentVersionId(),
                    submission.getOverallSnapshot(),
                    submission.getArticleSnapshots(),
                    task.getTaskId(),
                    submission.getSubmissionId(),
                    round.getReviewerId(),
                    round.getCompletionStartedAt());
            try {
                return annotationVersionRepository.insert(candidate);
            } catch (DuplicateKeyException exception) {
                AnnotationVersionDocument byTask = annotationVersionRepository
                        .findBySourceTaskId(task.getTaskId())
                        .orElse(null);
                if (byTask != null) {
                    validateExistingAnnotationVersion(byTask, task, round, submission);
                    return byTask;
                }
            }
        }
        throw writeConflict();
    }

    private void ensureRoundAnnotationVersion(
            ReviewRoundDocument round,
            AnnotationVersionDocument version) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(round.getReviewRoundId())
                        .and("completionOutcome").is(ReviewRoundOutcome.APPROVED)
                        .orOperator(
                                Criteria.where("annotationVersionId").is(null),
                                Criteria.where("annotationVersionId").is(version.getId()))),
                new Update().set("annotationVersionId", version.getId()),
                ReviewRoundDocument.class);
        ReviewRoundDocument current = reviewRoundRepository.findById(round.getReviewRoundId())
                .orElseThrow(ReviewService::reviewNotFound);
        if (!Objects.equals(current.getAnnotationVersionId(), version.getId())) {
            throw writeConflict();
        }
    }

    private void ensureLawAnnotationReference(
            TaskDocument task,
            AnnotationVersionDocument version) {
        Criteria annotationReference;
        Update update = new Update()
                .set("currentAnnotationVersionId", version.getId())
                .set("updatedAt", Instant.now());
        if (task.getTaskType() == TaskType.REVISION) {
            RevisionMode mode = task.getRevisionScope().mode();
            Criteria beforeCompletion = new Criteria().andOperator(
                    Criteria.where("currentAnnotationVersionId")
                            .is(task.getBaseAnnotationVersionId()),
                    mode == RevisionMode.CONTENT_CHANGE
                            ? Criteria.where("pendingRevision").is(true)
                            : emptyPendingCriteria());
            Criteria recovered = new Criteria().andOperator(
                    Criteria.where("currentAnnotationVersionId").is(version.getId()),
                    emptyPendingCriteria());
            annotationReference = new Criteria().orOperator(beforeCompletion, recovered);
            if (mode == RevisionMode.CONTENT_CHANGE) {
                update.set("pendingRevision", false)
                        .set("pendingChangeSet", PendingChangeSet.empty());
            }
        } else {
            annotationReference = new Criteria().orOperator(
                    Criteria.where("currentAnnotationVersionId").is(null),
                    Criteria.where("currentAnnotationVersionId").is(version.getId()));
        }
        Criteria criteria = new Criteria().andOperator(
                Criteria.where("_id").is(task.getLawId()),
                Criteria.where("currentContentVersionId").is(task.getContentVersionId()),
                annotationReference);
        mongoTemplate.updateFirst(
                Query.query(criteria),
                update,
                LawDocument.class);
        LawDocument law = mongoTemplate.findById(task.getLawId(), LawDocument.class);
        if (law == null
                || !Objects.equals(law.getCurrentContentVersionId(), task.getContentVersionId())
                || !Objects.equals(law.getCurrentAnnotationVersionId(), version.getId())
                || (task.getTaskType() == TaskType.REVISION
                        && (law.isPendingRevision()
                                || !law.getPendingChangeSet().isEmpty()))) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ReviewErrorCodes.COMPLETION_CONFLICT,
                    "法律内容版本或正式标注引用已变化，无法完成审核");
        }
    }

    private void ensureTaskApproved(
            TaskDocument task,
            ReviewRoundDocument round,
            AnnotationVersionDocument version) {
        TaskState expected = expectedPendingState(round);
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(task.getTaskId())
                        .and("taskState").is(expected)
                        .and("currentReviewRoundId").is(round.getReviewRoundId())
                        .and("currentSubmissionId").is(round.getSourceSubmissionId())),
                new Update()
                        .set("taskState", TaskState.APPROVED)
                        .set("approvedAnnotationVersionId", version.getId())
                        .set("updatedAt", Instant.now()),
                TaskDocument.class);
        TaskDocument current = requireTask(task.getTaskId());
        if (current.getTaskState() != TaskState.APPROVED
                || !Objects.equals(current.getApprovedAnnotationVersionId(), version.getId())) {
            throw writeConflict();
        }
    }

    private void ensureTaskRejected(TaskDocument task, ReviewRoundDocument round) {
        TaskState expected = expectedPendingState(round);
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(task.getTaskId())
                        .and("taskState").is(expected)
                        .and("currentReviewRoundId").is(round.getReviewRoundId())
                        .and("currentSubmissionId").is(round.getSourceSubmissionId())),
                new Update()
                        .set("taskState", TaskState.PARTIALLY_REJECTED)
                        .set("updatedAt", Instant.now()),
                TaskDocument.class);
        TaskDocument current = requireTask(task.getTaskId());
        if (current.getTaskState() != TaskState.PARTIALLY_REJECTED
                || !Objects.equals(current.getCurrentReviewRoundId(), round.getReviewRoundId())) {
            throw writeConflict();
        }
    }

    private ReviewRoundDocument updateItem(
            ReviewContext context,
            ReviewItemLocator locator,
            ReviewItemState currentState,
            Update update) {
        ReviewRoundDocument updated = mongoTemplate.findAndModify(
                baseWritableRoundQuery(context)
                        .addCriteria(Criteria.where(itemStatePath(locator)).is(currentState)),
                update,
                FindAndModifyOptions.options().returnNew(true),
                ReviewRoundDocument.class);
        if (updated == null) {
            throw writeConflict();
        }
        return updated;
    }

    private ReviewContext requireWritableContext(
            String taskId,
            String roundId,
            ReviewItemLocator locator,
            UserPrincipal currentUser) {
        requireAdmin(currentUser);
        TaskDocument task = requireTask(taskId);
        ReviewRoundDocument round = requireRound(task, roundId);
        requireReviewer(round, currentUser);
        if (task.getTaskState() != expectedPendingState(round)
                || !Objects.equals(task.getCurrentReviewRoundId(), round.getReviewRoundId())
                || round.getCompletionStartedAt() != null
                || round.getCompletedAt() != null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ReviewErrorCodes.INVALID_TASK_STATE,
                    "审核轮次当前不可写");
        }
        validateLocator(task, locator);
        return new ReviewContext(task, round);
    }

    private Query baseWritableRoundQuery(ReviewContext context) {
        return Query.query(Criteria.where("_id").is(context.round().getReviewRoundId())
                .and("reviewerId").is(context.round().getReviewerId())
                .and("completedAt").is(null)
                .and("completionStartedAt").is(null));
    }

    private ReviewRoundDocument requirePreviousRejectedRound(
            TaskDocument task,
            TaskSubmissionDocument source) {
        if (source.getSourceReviewRoundId() == null) {
            throw sourceInvalid("复审提交缺少来源审核轮次");
        }
        ReviewRoundDocument previous = reviewRoundRepository
                .findById(source.getSourceReviewRoundId())
                .filter(round -> task.getTaskId().equals(round.getTaskId()))
                .orElseThrow(() -> sourceInvalid("复审提交的来源审核轮次不存在"));
        if (previous.getCompletedAt() == null
                || previous.getCompletionOutcome() != ReviewRoundOutcome.PARTIALLY_REJECTED) {
            throw sourceInvalid("复审提交必须来源于已完成的部分驳回轮次");
        }
        return previous;
    }

    private static List<ReviewItemLocator> initialScope(TaskDocument task) {
        if (task.getTaskType() == TaskType.REVISION) {
            if (task.getRevisionScope() == null) {
                throw sourceInvalid("修订任务缺少冻结revisionScope");
            }
            return task.getRevisionScope().toReviewScope();
        }
        LinkedHashSet<ReviewItemLocator> scope = new LinkedHashSet<>();
        scope.add(ReviewItemLocator.overall());
        task.getContentVersionSnapshot().articles().forEach(
                article -> scope.add(ReviewItemLocator.article(article.articleId())));
        return List.copyOf(scope);
    }

    private static List<ReviewItemLocator> normalizedScope(
            TaskDocument task,
            List<ReviewItemLocator> source) {
        LinkedHashSet<ReviewItemLocator> result = new LinkedHashSet<>();
        if (source != null) {
            for (ReviewItemLocator locator : source) {
                validateLocator(task, locator);
                result.add(locator);
            }
        }
        return List.copyOf(result);
    }

    private static void validateLocator(TaskDocument task, ReviewItemLocator locator) {
        if (locator == null) {
            throw itemNotInScope();
        }
        if (locator.type() == ReviewScopeType.ARTICLE
                && task.getContentVersionSnapshot().articles().stream()
                        .noneMatch(article -> article.articleId().equals(locator.articleId()))) {
            throw itemNotInScope();
        }
    }

    private static void validateFrozenSubmission(
            TaskDocument task,
            TaskSubmissionDocument submission) {
        if (task.getContentVersionSnapshot() == null
                || !Objects.equals(
                        task.getContentVersionId(),
                        task.getContentVersionSnapshot().contentVersionId())
                || task.getContentVersionSnapshot().articles() == null) {
            throw sourceInvalid("任务内容版本引用与任务快照不一致");
        }
        if (submission.getOverallSnapshot() == null) {
            throw sourceInvalid("冻结提交缺少整体标注");
        }
        Set<String> expectedArticleIds = new LinkedHashSet<>();
        task.getContentVersionSnapshot().articles().forEach(
                article -> expectedArticleIds.add(article.articleId()));
        if (!submission.getArticleSnapshots().keySet().equals(expectedArticleIds)
                || submission.getArticleSnapshots().values().stream().anyMatch(Objects::isNull)) {
            throw sourceInvalid("冻结提交与任务内容快照不一致");
        }
    }

    private static void validateExistingAnnotationVersion(
            AnnotationVersionDocument version,
            TaskDocument task,
            ReviewRoundDocument round,
            TaskSubmissionDocument submission) {
        if (!Objects.equals(version.getSourceTaskId(), task.getTaskId())
                || !Objects.equals(version.getLawId(), task.getLawId())
                || !Objects.equals(version.getContentVersionId(), task.getContentVersionId())
                || !Objects.equals(version.getSourceSubmissionId(), round.getSourceSubmissionId())
                || !Objects.equals(version.getApprovedBy(), round.getReviewerId())
                || !Objects.equals(version.getApprovedAt(), round.getCompletionStartedAt())
                || !Objects.equals(version.getOverallResult(), submission.getOverallSnapshot())
                || !Objects.equals(version.getArticleResults(), submission.getArticleSnapshots())) {
            throw completionConflict("已有正式标注版本与本次完成意图不一致");
        }
    }

    private static void validateRevisionLawBasis(
            LawDocument law,
            TaskDocument task,
            AnnotationVersionDocument existing) {
        if (task.getRevisionScope() == null
                || task.getBaseAnnotationVersionId() == null) {
            throw completionConflict("修订任务缺少冻结修订基线");
        }
        String currentAnnotationVersionId = law.getCurrentAnnotationVersionId();
        boolean beforeCompletion = Objects.equals(
                currentAnnotationVersionId, task.getBaseAnnotationVersionId());
        boolean recovered = existing != null
                && Objects.equals(currentAnnotationVersionId, existing.getId());
        if (!beforeCompletion && !recovered) {
            throw completionConflict("法律当前正式标注引用与修订基线不一致");
        }
        boolean pendingEmpty = law.getPendingChangeSet() != null
                && law.getPendingChangeSet().isEmpty();
        if (task.getRevisionScope().mode() == RevisionMode.CONTENT_CHANGE) {
            if ((beforeCompletion && (!law.isPendingRevision() || pendingEmpty))
                    || (recovered && (law.isPendingRevision() || !pendingEmpty))) {
                throw completionConflict("正文变更修订的pending状态与完成意图不一致");
            }
        } else if (law.isPendingRevision() || !pendingEmpty) {
            throw completionConflict("标注修正型修订检测到未处理的semantic pending状态");
        }
    }

    private static Criteria emptyPendingCriteria() {
        return new Criteria().andOperator(
                Criteria.where("pendingRevision").is(false),
                Criteria.where("pendingChangeSet.addedArticleIds").size(0),
                Criteria.where("pendingChangeSet.modifiedArticleIds").size(0),
                Criteria.where("pendingChangeSet.deletedArticleIds").size(0));
    }

    private ReviewDetailResponse toResponse(
            TaskDocument task,
            ReviewRoundDocument round,
            UserPrincipal currentUser) {
        TaskSubmissionDocument previousSubmission = round.getPreviousSubmissionId() == null
                ? null
                : submissionRepository.findById(round.getPreviousSubmissionId()).orElse(null);
        ReviewSubmissionSnapshotResponse before = previousSubmission == null
                ? null
                : ReviewSubmissionSnapshotResponse.from(previousSubmission);
        if (before == null
                && task.getTaskType() == TaskType.REVISION
                && round.getRoundType() == ReviewRoundType.INITIAL_REVIEW) {
            AnnotationVersionDocument base = annotationVersionRepository
                    .findById(task.getBaseAnnotationVersionId())
                    .orElseThrow(() -> sourceInvalid("修订任务基础正式标注版本不存在"));
            before = ReviewSubmissionSnapshotResponse.from(base);
        }
        TaskSubmissionDocument after = submissionRepository.findById(round.getSourceSubmissionId())
                .orElseThrow(() -> sourceInvalid("审核来源提交不存在"));
        List<ReviewItemResponse> items = round.getRequiredScope().stream()
                .map(locator -> new ReviewItemResponse(
                        locator,
                        round.getItemStates().get(locator.storageKey()),
                        round.getIssues().get(locator.storageKey())))
                .toList();
        boolean writable = currentUser != null
                && currentUser.role() == Role.ADMIN
                && Objects.equals(currentUser.id(), round.getReviewerId())
                && task.getTaskState() == expectedPendingState(round)
                && round.getCompletionStartedAt() == null
                && round.getCompletedAt() == null;
        return new ReviewDetailResponse(
                task.getTaskId(),
                round.getReviewRoundId(),
                round.getRoundNo(),
                round.getRoundType(),
                task.getTaskState(),
                round.getReviewerId(),
                writable,
                new ReviewProgressResponse(
                        round.getTotalCount(),
                        round.getReviewedCount(),
                        round.getUnreviewedCount(),
                        round.getNeedsChangeCount()),
                items,
                task.getContentVersionSnapshot(),
                task.getLawBaseInfoSnapshot(),
                task.getStructureSnapshot(),
                task.getFieldConfigSnapshot(),
                before,
                ReviewSubmissionSnapshotResponse.from(after),
                round.getCompletionOutcome(),
                round.getAnnotationVersionId(),
                round.getStartedAt(),
                round.getCompletionStartedAt(),
                round.getCompletedAt());
    }

    private TaskDocument requireTask(String taskId) {
        String validTaskId = requireIdentifier(taskId, "taskId");
        return taskRepository.findById(validTaskId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                TaskErrorCodes.NOT_FOUND,
                "任务不存在"));
    }

    private ReviewRoundDocument requireRound(TaskDocument task, String roundId) {
        String validRoundId = requireIdentifier(roundId, "roundId");
        return reviewRoundRepository.findById(validRoundId)
                .filter(round -> task.getTaskId().equals(round.getTaskId()))
                .orElseThrow(ReviewService::reviewNotFound);
    }

    private TaskSubmissionDocument requireSubmission(TaskDocument task, String submissionId) {
        if (submissionId == null) {
            throw sourceInvalid("任务缺少当前冻结提交引用");
        }
        return submissionRepository.findById(submissionId)
                .filter(submission -> task.getTaskId().equals(submission.getTaskId()))
                .orElseThrow(() -> sourceInvalid("任务当前冻结提交不存在"));
    }

    private static ReviewRoundType roundTypeFor(TaskState taskState) {
        if (taskState == TaskState.PENDING_REVIEW) {
            return ReviewRoundType.INITIAL_REVIEW;
        }
        if (taskState == TaskState.PENDING_REREVIEW) {
            return ReviewRoundType.REREVIEW;
        }
        throw new ApiException(
                HttpStatus.CONFLICT,
                ReviewErrorCodes.INVALID_TASK_STATE,
                "任务当前状态不允许开始审核：" + taskState);
    }

    private static TaskState expectedPendingState(ReviewRoundDocument round) {
        return round.getRoundType() == ReviewRoundType.INITIAL_REVIEW
                ? TaskState.PENDING_REVIEW
                : TaskState.PENDING_REREVIEW;
    }

    private static void requireAdmin(UserPrincipal currentUser) {
        if (currentUser == null || currentUser.role() != Role.ADMIN) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    AuthErrorCodes.FORBIDDEN,
                    "无权执行此操作");
        }
    }

    private static void requireReviewer(
            ReviewRoundDocument round,
            UserPrincipal currentUser) {
        if (!Objects.equals(round.getReviewerId(), currentUser.id())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    ReviewErrorCodes.NOT_REVIEWER,
                    "只有本轮审核员可以修改或完成审核");
        }
    }

    private static String requireReason(String reason) {
        if (reason == null) {
            throw invalidReason("不能为空");
        }
        String value = reason.trim();
        int length = value.codePointCount(0, value.length());
        boolean containsControl = value.codePoints().anyMatch(Character::isISOControl);
        if (length < 1 || length > 500 || containsControl) {
            throw invalidReason("trim后须为1至500个字符且不得包含控制字符");
        }
        return value;
    }

    private static String requireIdentifier(String value, String path) {
        if (value == null || value.isBlank()) {
            throw validation(path, "不能为空");
        }
        String valid = value.trim();
        if (valid.codePointCount(0, valid.length()) > 100
                || valid.codePoints().anyMatch(Character::isISOControl)) {
            throw validation(path, "须为1至100个字符且不得包含控制字符");
        }
        return valid;
    }

    private static String itemStatePath(ReviewItemLocator locator) {
        return "itemStates." + locator.storageKey();
    }

    private static String issuePath(ReviewItemLocator locator) {
        return "issues." + locator.storageKey();
    }

    private static ApiException incomplete(int unreviewed) {
        return new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ReviewErrorCodes.INCOMPLETE,
                "仍有" + unreviewed + "个审核项未处理");
    }

    private static ApiException alreadyCompleted() {
        return new ApiException(
                HttpStatus.CONFLICT,
                ReviewErrorCodes.ALREADY_COMPLETED,
                "本轮审核已经完成");
    }

    private static ApiException itemNotInScope() {
        return new ApiException(
                HttpStatus.CONFLICT,
                ReviewErrorCodes.ITEM_NOT_IN_SCOPE,
                "审核项不在本轮可处理范围内");
    }

    private static ApiException reviewNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                ReviewErrorCodes.NOT_FOUND,
                "审核轮次不存在");
    }

    private static ApiException sourceInvalid(String message) {
        return new ApiException(
                HttpStatus.CONFLICT,
                ReviewErrorCodes.SOURCE_INVALID,
                message);
    }

    private static ApiException writeConflict() {
        return completionConflict("审核状态已变化，请刷新后重试");
    }

    private static ApiException completionConflict(String message) {
        return new ApiException(
                HttpStatus.CONFLICT,
                ReviewErrorCodes.COMPLETION_CONFLICT,
                message);
    }

    private static ApiException validation(String path, String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "COMMON.VALIDATION_FAILED",
                "请求参数校验失败",
                List.of(new ErrorLocator(path, message)));
    }

    private static ApiException invalidReason(String message) {
        return new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ReviewErrorCodes.ISSUE_REASON_INVALID,
                "审核问题原因无效",
                List.of(new ErrorLocator("reason", message)));
    }

    private record ReviewContext(TaskDocument task, ReviewRoundDocument round) {
    }

    private record ApprovedCompletionContext(
            TaskDocument task,
            ReviewRoundDocument round,
            TaskSubmissionDocument submission,
            AnnotationVersionDocument existingVersion) {
    }
}
