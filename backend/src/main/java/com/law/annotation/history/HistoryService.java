package com.law.annotation.history;

import com.law.annotation.annotation.TaskSubmissionDocument;
import com.law.annotation.auth.AuthErrorCodes;
import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.history.dto.AnnotationVersionHistoryResponse;
import com.law.annotation.history.dto.ContentVersionHistoryResponse;
import com.law.annotation.history.dto.HistoryTimelineItemResponse;
import com.law.annotation.history.dto.HistoryTimelineItemResponse.DetailRef;
import com.law.annotation.history.dto.LawAuditHistoryResponse;
import com.law.annotation.history.dto.LawHistoryResponse;
import com.law.annotation.history.dto.TaskHistoryResponse;
import com.law.annotation.history.dto.TaskHistoryResponse.ArticleResult;
import com.law.annotation.law.LawAuditDocument;
import com.law.annotation.law.LawAuditType;
import com.law.annotation.law.LawErrorCodes;
import com.law.annotation.review.ReviewIssue;
import com.law.annotation.review.ReviewItemLocator;
import com.law.annotation.review.ReviewRoundDocument;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.task.TaskErrorCodes;
import com.law.annotation.version.AnnotationVersionDocument;
import com.law.annotation.version.ContentVersionDocument;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class HistoryService {

    private static final Comparator<HistoryTimelineItemResponse> TIMELINE_ORDER = Comparator
            .comparing(HistoryTimelineItemResponse::occurredAt, Comparator.reverseOrder())
            .thenComparingInt(item -> typePriority(item.type()))
            .thenComparing(HistoryTimelineItemResponse::eventId);

    private final HistoryQueryRepository repository;

    public HistoryService(HistoryQueryRepository repository) {
        this.repository = repository;
    }

    public LawHistoryResponse getLawHistory(String lawId, UserPrincipal principal) {
        requireAdmin(principal);
        HistoryQueryRepository.LawStatus law = requireLaw(lawId);
        List<HistoryTimelineItemResponse> timeline = new ArrayList<>();

        repository.findContentSummaries(lawId).forEach(version -> timeline.add(item(
                HistoryCategory.CONTENT_VERSION,
                HistoryItemType.CONTENT_VERSION_CREATED,
                version.getId(),
                null,
                version.getCreatedBy(),
                version.getCreatedAt(),
                "内容版本 C" + version.getSeq() + " 已创建",
                HistoryDetailType.CONTENT_VERSION,
                version.getId())));

        repository.findAuditSummaries(lawId).forEach(audit -> {
            HistoryItemType type = audit.getAuditType() == LawAuditType.STRUCTURE
                    ? HistoryItemType.LAW_STRUCTURE_CHANGED
                    : HistoryItemType.LAW_METADATA_CHANGED;
            timeline.add(item(
                    HistoryCategory.LAW_AUDIT,
                    type,
                    audit.getId(),
                    null,
                    audit.getOperatorId(),
                    audit.getOperatedAt(),
                    type == HistoryItemType.LAW_STRUCTURE_CHANGED ? "法律结构已变更" : "法律基础信息已变更",
                    HistoryDetailType.LAW_AUDIT,
                    audit.getId()));
        });

        repository.findAnnotationSummaries(lawId).forEach(version -> timeline.add(item(
                HistoryCategory.ANNOTATION_VERSION,
                HistoryItemType.ANNOTATION_VERSION_APPROVED,
                version.getId(),
                version.getSourceTaskId(),
                version.getApprovedBy(),
                version.getApprovedAt(),
                "正式标注版本 A" + version.getSeq() + " 已批准",
                HistoryDetailType.ANNOTATION_VERSION,
                version.getId())));

        List<HistoryQueryRepository.TaskSummary> tasks = repository.findTaskSummaries(lawId);
        List<String> taskIds = tasks.stream().map(HistoryQueryRepository.TaskSummary::getTaskId).toList();
        tasks.forEach(task -> {
            timeline.add(item(
                    HistoryCategory.TASK,
                    HistoryItemType.TASK_CREATED,
                    task.getTaskId(),
                    task.getTaskId(),
                    task.getCreatedBy(),
                    task.getCreatedAt(),
                    "任务“" + safeTaskName(task.getTaskName()) + "”已创建",
                    HistoryDetailType.TASK,
                    task.getTaskId()));
            if (task.getCanceledAt() != null) {
                timeline.add(item(
                        HistoryCategory.CANCELLATION,
                        HistoryItemType.TASK_CANCELED,
                        task.getTaskId(),
                        task.getTaskId(),
                        task.getCanceledBy(),
                        task.getCanceledAt(),
                        "任务已取消",
                        HistoryDetailType.TASK,
                        task.getTaskId()));
            }
        });

        repository.findSubmissionSummaries(taskIds).forEach(submission -> {
            HistoryItemType type = submission.getSubmissionNo() == 1
                    ? HistoryItemType.TASK_SUBMITTED
                    : HistoryItemType.TASK_REREVIEW_SUBMITTED;
            timeline.add(item(
                    HistoryCategory.SUBMISSION,
                    type,
                    submission.getSubmissionId(),
                    submission.getTaskId(),
                    submission.getSubmittedBy(),
                    submission.getSubmittedAt(),
                    type == HistoryItemType.TASK_SUBMITTED ? "任务已首次提交审核" : "任务已提交复审",
                    HistoryDetailType.TASK,
                    submission.getTaskId()));
        });

        repository.findReviewSummaries(taskIds).forEach(round -> addReviewEvents(timeline, round));
        timeline.sort(TIMELINE_ORDER);
        return new LawHistoryResponse(lawId, law.isDeleted(), law.getDeletedAt(), timeline);
    }

    public ContentVersionHistoryResponse getContentVersion(
            String lawId, String contentVersionId, UserPrincipal principal) {
        requireAdmin(principal);
        requireLaw(lawId);
        ContentVersionDocument version = repository.findContentVersion(lawId, contentVersionId)
                .orElseThrow(HistoryService::itemNotFound);
        return new ContentVersionHistoryResponse(
                version.getId(), version.getLawId(), version.getSeq(),
                version.getSemanticArticlesSnapshot(), version.getCreatedBy(), version.getCreatedAt());
    }

    public AnnotationVersionHistoryResponse getAnnotationVersion(
            String lawId, String annotationVersionId, UserPrincipal principal) {
        requireAdmin(principal);
        requireLaw(lawId);
        AnnotationVersionDocument version = repository.findAnnotationVersion(lawId, annotationVersionId)
                .orElseThrow(HistoryService::itemNotFound);
        ContentVersionDocument content = repository.findContentVersion(lawId, version.getContentVersionId())
                .orElseThrow(HistoryService::snapshotInconsistent);
        List<String> articleIds = content.getSemanticArticlesSnapshot().stream()
                .map(article -> article.getArticleId())
                .toList();
        requireExactArticleSet(articleIds, version.getArticleResults().keySet());
        List<AnnotationVersionHistoryResponse.ArticleResult> results = articleIds.stream()
                .map(articleId -> new AnnotationVersionHistoryResponse.ArticleResult(
                        articleId, version.getArticleResults().get(articleId)))
                .toList();
        return new AnnotationVersionHistoryResponse(
                version.getId(), version.getLawId(), version.getSeq(), version.getContentVersionId(),
                version.getOverallResult(), results, version.getSourceTaskId(),
                version.getSourceSubmissionId(), version.getApprovedBy(), version.getApprovedAt());
    }

    public LawAuditHistoryResponse getAudit(String lawId, String auditId, UserPrincipal principal) {
        requireAdmin(principal);
        requireLaw(lawId);
        LawAuditDocument audit = repository.findAudit(lawId, auditId)
                .orElseThrow(HistoryService::itemNotFound);
        return new LawAuditHistoryResponse(
                audit.getId(), audit.getLawId(), audit.getAuditType(), audit.getBefore(), audit.getAfter(),
                audit.getOperatorId(), audit.getOperatedAt());
    }

    public TaskHistoryResponse getTask(String lawId, String taskId, UserPrincipal principal) {
        TaskDocument task = repository.findTask(lawId, taskId)
                .orElseThrow(HistoryService::taskNotFound);
        if (principal == null) {
            throw forbidden();
        }
        if (principal.role() == Role.ANNOTATOR && !principal.id().equals(task.getAnnotatorId())) {
            throw taskNotFound();
        }
        if (principal.role() != Role.ADMIN && principal.role() != Role.ANNOTATOR) {
            throw forbidden();
        }
        HistoryQueryRepository.LawStatus law = repository.findLaw(lawId)
                .orElseThrow(HistoryService::taskNotFound);
        validateTaskSnapshot(task);
        List<String> articleIds = task.getContentVersionSnapshot().articles().stream()
                .map(article -> article.articleId())
                .toList();
        List<TaskHistoryResponse.Submission> submissions = repository.findTaskSubmissions(taskId).stream()
                .map(submission -> mapSubmission(submission, articleIds))
                .toList();
        List<TaskHistoryResponse.ReviewRound> reviewRounds = repository.findTaskReviewRounds(taskId).stream()
                .map(HistoryService::mapReviewRound)
                .toList();
        validateTaskReferences(task, submissions, reviewRounds);
        return new TaskHistoryResponse(
                task.getTaskId(), task.getTaskType(), task.getTaskState(), task.getTaskName(), task.getRemark(),
                task.getLawId(), law.isDeleted(), law.getDeletedAt(), task.getAnnotatorId(),
                task.getAnnotatorNameSnapshot(), task.getCreatedBy(), task.getCreatedAt(), task.getUpdatedAt(),
                task.getContentVersionId(), task.getContentVersionSnapshot(), task.getLawBaseInfoSnapshot(),
                task.getStructureSnapshot(), task.getFieldConfigSnapshot(), task.getBaseAnnotationVersionId(),
                task.getRevisionScope(), task.getInitialSubmissionId(), task.getCurrentSubmissionId(),
                task.getCurrentReviewRoundId(), task.getApprovedAnnotationVersionId(),
                task.getCancelReason(), task.getCanceledBy(), task.getCanceledAt(),
                submissions, reviewRounds);
    }

    private static void addReviewEvents(
            List<HistoryTimelineItemResponse> timeline,
            HistoryQueryRepository.ReviewSummary round) {
        if (round.getStartedAt() != null) {
            timeline.add(item(
                    HistoryCategory.REVIEW, HistoryItemType.REVIEW_STARTED,
                    round.getReviewRoundId(), round.getTaskId(), round.getReviewerId(), round.getStartedAt(),
                    "第" + round.getRoundNo() + "轮审核已开始", HistoryDetailType.TASK, round.getTaskId()));
        }
        round.getIssues().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    ReviewIssue issue = entry.getValue();
                    timeline.add(item(
                            HistoryCategory.REVIEW, HistoryItemType.REVIEW_ISSUE_CREATED,
                            round.getReviewRoundId() + ":" + entry.getKey(), round.getTaskId(),
                            round.getReviewerId(), issue.createdAt(), "审核问题已提出",
                            HistoryDetailType.TASK, round.getTaskId()));
                });
        if (round.getCompletedAt() != null) {
            timeline.add(item(
                    HistoryCategory.REVIEW, HistoryItemType.REVIEW_COMPLETED,
                    round.getReviewRoundId(), round.getTaskId(), round.getReviewerId(), round.getCompletedAt(),
                    "第" + round.getRoundNo() + "轮审核已完成", HistoryDetailType.TASK, round.getTaskId()));
        }
    }

    private static HistoryTimelineItemResponse item(
            HistoryCategory category,
            HistoryItemType type,
            String entityId,
            String taskId,
            String actorId,
            Instant occurredAt,
            String summary,
            HistoryDetailType detailType,
            String detailId) {
        if (entityId == null || occurredAt == null) {
            throw snapshotInconsistent();
        }
        return new HistoryTimelineItemResponse(
                type.name() + ":" + entityId, category, type, entityId, taskId, actorId,
                occurredAt, summary, new DetailRef(detailType, detailId));
    }

    private static TaskHistoryResponse.Submission mapSubmission(
            TaskSubmissionDocument submission, List<String> articleIds) {
        requireExactArticleSet(articleIds, submission.getArticleSnapshots().keySet());
        List<ArticleResult> ordered = articleIds.stream()
                .map(articleId -> new ArticleResult(articleId, submission.getArticleSnapshots().get(articleId)))
                .toList();
        return new TaskHistoryResponse.Submission(
                submission.getSubmissionId(), submission.getSubmissionNo(), submission.getDraftRevision(),
                submission.getOverallSnapshot(), ordered, submission.getSourceReviewRoundId(),
                submission.getModifiedScope(), submission.getSubmittedBy(), submission.getSubmittedAt());
    }

    private static TaskHistoryResponse.ReviewRound mapReviewRound(ReviewRoundDocument round) {
        Map<String, ReviewItemLocator> locators = round.getRequiredScope().stream()
                .collect(Collectors.toMap(
                        ReviewItemLocator::storageKey,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        List<TaskHistoryResponse.ItemState> itemStates = new ArrayList<>();
        locators.forEach((key, locator) -> {
            if (round.getItemStates().containsKey(key)) {
                itemStates.add(new TaskHistoryResponse.ItemState(locator, round.getItemStates().get(key)));
            }
        });
        round.getItemStates().entrySet().stream()
                .filter(entry -> !locators.containsKey(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    ReviewIssue issue = round.getIssues().get(entry.getKey());
                    if (issue == null) {
                        throw snapshotInconsistent();
                    }
                    itemStates.add(new TaskHistoryResponse.ItemState(
                            new ReviewItemLocator(issue.scopeType(), issue.articleId()), entry.getValue()));
                });
        List<TaskHistoryResponse.ReviewIssueHistory> issues = round.getIssues().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    ReviewIssue issue = entry.getValue();
                    return new TaskHistoryResponse.ReviewIssueHistory(
                            new ReviewItemLocator(issue.scopeType(), issue.articleId()),
                            issue.reason(), round.getReviewerId(), issue.createdAt());
                })
                .toList();
        return new TaskHistoryResponse.ReviewRound(
                round.getReviewRoundId(), round.getRoundNo(), round.getRoundType(),
                round.getSourceSubmissionId(), round.getPreviousSubmissionId(), round.getReviewerId(),
                round.getRequiredScope(), itemStates, issues, round.getTotalCount(), round.getReviewedCount(),
                round.getUnreviewedCount(), round.getNeedsChangeCount(), round.getCompletionOutcome(),
                round.getCompletionStartedAt(), round.getAnnotationVersionId(), round.getCreatedAt(),
                round.getStartedAt(), round.getCompletedAt());
    }

    private static void validateTaskSnapshot(TaskDocument task) {
        if (task.getContentVersionSnapshot() == null
                || task.getLawBaseInfoSnapshot() == null
                || task.getStructureSnapshot() == null
                || task.getFieldConfigSnapshot() == null
                || !task.getContentVersionId().equals(task.getContentVersionSnapshot().contentVersionId())) {
            throw snapshotInconsistent();
        }
        List<String> articleIds = task.getContentVersionSnapshot().articles().stream()
                .map(article -> article.articleId())
                .toList();
        if (new HashSet<>(articleIds).size() != articleIds.size()) {
            throw snapshotInconsistent();
        }
        if (task.getTaskType() == TaskType.REVISION
                && (task.getBaseAnnotationVersionId() == null || task.getRevisionScope() == null)) {
            throw snapshotInconsistent();
        }
    }

    private static void validateTaskReferences(
            TaskDocument task,
            List<TaskHistoryResponse.Submission> submissions,
            List<TaskHistoryResponse.ReviewRound> reviewRounds) {
        Set<String> submissionIds = submissions.stream()
                .map(TaskHistoryResponse.Submission::submissionId)
                .collect(Collectors.toSet());
        Set<String> reviewIds = reviewRounds.stream()
                .map(TaskHistoryResponse.ReviewRound::reviewRoundId)
                .collect(Collectors.toSet());
        if ((task.getInitialSubmissionId() != null && !submissionIds.contains(task.getInitialSubmissionId()))
                || (task.getCurrentSubmissionId() != null && !submissionIds.contains(task.getCurrentSubmissionId()))
                || (task.getCurrentReviewRoundId() != null && !reviewIds.contains(task.getCurrentReviewRoundId()))) {
            throw snapshotInconsistent();
        }
    }

    private static void requireExactArticleSet(List<String> orderedArticleIds, Set<String> actualIds) {
        if (orderedArticleIds.size() != actualIds.size()
                || !new HashSet<>(orderedArticleIds).equals(actualIds)) {
            throw snapshotInconsistent();
        }
    }

    private HistoryQueryRepository.LawStatus requireLaw(String lawId) {
        return repository.findLaw(lawId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, LawErrorCodes.NOT_FOUND, "法律不存在"));
    }

    private static void requireAdmin(UserPrincipal principal) {
        if (principal == null || principal.role() != Role.ADMIN) {
            throw forbidden();
        }
    }

    private static String safeTaskName(String taskName) {
        return taskName == null || taskName.isBlank() ? "未命名任务" : taskName;
    }

    private static int typePriority(HistoryItemType type) {
        return switch (type) {
            case CONTENT_VERSION_CREATED -> 10;
            case LAW_METADATA_CHANGED -> 20;
            case LAW_STRUCTURE_CHANGED -> 30;
            case TASK_CREATED -> 40;
            case TASK_SUBMITTED -> 50;
            case TASK_REREVIEW_SUBMITTED -> 60;
            case REVIEW_STARTED -> 70;
            case REVIEW_ISSUE_CREATED -> 80;
            case REVIEW_COMPLETED -> 90;
            case ANNOTATION_VERSION_APPROVED -> 100;
            case TASK_CANCELED -> 110;
        };
    }

    private static ApiException itemNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, HistoryErrorCodes.ITEM_NOT_FOUND, "历史记录不存在");
    }

    private static ApiException taskNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, TaskErrorCodes.NOT_FOUND, "任务不存在");
    }

    private static ApiException snapshotInconsistent() {
        return new ApiException(
                HttpStatus.CONFLICT,
                HistoryErrorCodes.SNAPSHOT_INCONSISTENT,
                "历史快照数据不一致");
    }

    private static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, AuthErrorCodes.FORBIDDEN, "无权执行此操作");
    }
}
