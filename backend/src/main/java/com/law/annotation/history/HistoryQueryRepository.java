package com.law.annotation.history;

import com.law.annotation.annotation.TaskSubmissionDocument;
import com.law.annotation.law.LawAuditDocument;
import com.law.annotation.law.LawAuditType;
import com.law.annotation.review.ReviewIssue;
import com.law.annotation.review.ReviewRoundDocument;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.version.AnnotationVersionDocument;
import com.law.annotation.version.ContentVersionDocument;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class HistoryQueryRepository {

    private static final String CONTENT_VERSIONS = "content_versions";
    private static final String LAWS = "laws";
    private static final String LAW_AUDITS = "law_audits";
    private static final String ANNOTATION_VERSIONS = "annotation_versions";
    private static final String TASKS = "tasks";
    private static final String SUBMISSIONS = "task_submissions";
    private static final String REVIEW_ROUNDS = "review_rounds";

    private final MongoTemplate mongoTemplate;

    public HistoryQueryRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<LawStatus> findLaw(String lawId) {
        Query query = new Query(Criteria.where("_id").is(lawId));
        query.fields().include("deletedAt");
        return Optional.ofNullable(mongoTemplate.findOne(query, LawStatus.class, LAWS));
    }

    public List<ContentSummary> findContentSummaries(String lawId) {
        Query query = lawQuery(lawId);
        query.fields().include("seq").include("createdBy").include("createdAt");
        return mongoTemplate.find(query, ContentSummary.class, CONTENT_VERSIONS);
    }

    public List<AuditSummary> findAuditSummaries(String lawId) {
        Query query = lawQuery(lawId);
        query.fields().include("auditType").include("operatorId").include("operatedAt");
        return mongoTemplate.find(query, AuditSummary.class, LAW_AUDITS);
    }

    public List<AnnotationSummary> findAnnotationSummaries(String lawId) {
        Query query = lawQuery(lawId);
        query.fields().include("seq").include("sourceTaskId").include("approvedBy").include("approvedAt");
        return mongoTemplate.find(query, AnnotationSummary.class, ANNOTATION_VERSIONS);
    }

    public List<TaskSummary> findTaskSummaries(String lawId) {
        Query query = lawQuery(lawId);
        query.fields()
                .include("taskName")
                .include("createdBy")
                .include("createdAt")
                .include("cancelReason")
                .include("canceledBy")
                .include("canceledAt");
        return mongoTemplate.find(query, TaskSummary.class, TASKS);
    }

    public List<SubmissionSummary> findSubmissionSummaries(List<String> taskIds) {
        if (taskIds.isEmpty()) {
            return List.of();
        }
        Query query = new Query(Criteria.where("taskId").in(taskIds));
        query.fields()
                .include("taskId")
                .include("submissionNo")
                .include("sourceReviewRoundId")
                .include("submittedBy")
                .include("submittedAt");
        return mongoTemplate.find(query, SubmissionSummary.class, SUBMISSIONS);
    }

    public List<ReviewSummary> findReviewSummaries(List<String> taskIds) {
        if (taskIds.isEmpty()) {
            return List.of();
        }
        Query query = new Query(Criteria.where("taskId").in(taskIds));
        query.fields()
                .include("taskId")
                .include("roundNo")
                .include("reviewerId")
                .include("issues")
                .include("startedAt")
                .include("completedAt");
        return mongoTemplate.find(query, ReviewSummary.class, REVIEW_ROUNDS);
    }

    public Optional<ContentVersionDocument> findContentVersion(String lawId, String contentVersionId) {
        return Optional.ofNullable(mongoTemplate.findOne(
                scopedEntityQuery(lawId, contentVersionId), ContentVersionDocument.class));
    }

    public Optional<AnnotationVersionDocument> findAnnotationVersion(
            String lawId, String annotationVersionId) {
        return Optional.ofNullable(mongoTemplate.findOne(
                scopedEntityQuery(lawId, annotationVersionId), AnnotationVersionDocument.class));
    }

    public Optional<LawAuditDocument> findAudit(String lawId, String auditId) {
        return Optional.ofNullable(mongoTemplate.findOne(
                scopedEntityQuery(lawId, auditId), LawAuditDocument.class));
    }

    public Optional<TaskDocument> findTask(String lawId, String taskId) {
        return Optional.ofNullable(mongoTemplate.findOne(
                scopedEntityQuery(lawId, taskId), TaskDocument.class));
    }

    public List<TaskSubmissionDocument> findTaskSubmissions(String taskId) {
        Query query = new Query(Criteria.where("taskId").is(taskId));
        query.with(Sort.by(Sort.Direction.ASC, "submissionNo"));
        return mongoTemplate.find(query, TaskSubmissionDocument.class);
    }

    public List<ReviewRoundDocument> findTaskReviewRounds(String taskId) {
        Query query = new Query(Criteria.where("taskId").is(taskId));
        query.with(Sort.by(Sort.Direction.ASC, "roundNo"));
        return mongoTemplate.find(query, ReviewRoundDocument.class);
    }

    private static Query lawQuery(String lawId) {
        return new Query(Criteria.where("lawId").is(lawId));
    }

    private static Query scopedEntityQuery(String lawId, String entityId) {
        return new Query(Criteria.where("_id").is(entityId).and("lawId").is(lawId));
    }

    public static class LawStatus {
        @Id private String id;
        private Instant deletedAt;
        public LawStatus() { }
        public LawStatus(String id, Instant deletedAt) {
            this.id = id; this.deletedAt = deletedAt;
        }
        public String getId() { return id; }
        public Instant getDeletedAt() { return deletedAt; }
        public boolean isDeleted() { return deletedAt != null; }
    }

    public static class ContentSummary {
        @Id private String id;
        private int seq;
        private String createdBy;
        private Instant createdAt;
        public ContentSummary() { }
        public ContentSummary(String id, int seq, String createdBy, Instant createdAt) {
            this.id = id; this.seq = seq; this.createdBy = createdBy; this.createdAt = createdAt;
        }
        public String getId() { return id; }
        public int getSeq() { return seq; }
        public String getCreatedBy() { return createdBy; }
        public Instant getCreatedAt() { return createdAt; }
    }

    public static class AuditSummary {
        @Id private String id;
        private LawAuditType auditType;
        private String operatorId;
        private Instant operatedAt;
        public AuditSummary() { }
        public AuditSummary(String id, LawAuditType auditType, String operatorId, Instant operatedAt) {
            this.id = id; this.auditType = auditType; this.operatorId = operatorId; this.operatedAt = operatedAt;
        }
        public String getId() { return id; }
        public LawAuditType getAuditType() { return auditType; }
        public String getOperatorId() { return operatorId; }
        public Instant getOperatedAt() { return operatedAt; }
    }

    public static class AnnotationSummary {
        @Id private String id;
        private int seq;
        private String sourceTaskId;
        private String approvedBy;
        private Instant approvedAt;
        public AnnotationSummary() { }
        public AnnotationSummary(
                String id, int seq, String sourceTaskId, String approvedBy, Instant approvedAt) {
            this.id = id; this.seq = seq; this.sourceTaskId = sourceTaskId;
            this.approvedBy = approvedBy; this.approvedAt = approvedAt;
        }
        public String getId() { return id; }
        public int getSeq() { return seq; }
        public String getSourceTaskId() { return sourceTaskId; }
        public String getApprovedBy() { return approvedBy; }
        public Instant getApprovedAt() { return approvedAt; }
    }

    public static class TaskSummary {
        @Id private String taskId;
        private String taskName;
        private String createdBy;
        private Instant createdAt;
        private String cancelReason;
        private String canceledBy;
        private Instant canceledAt;
        public TaskSummary() { }
        public TaskSummary(
                String taskId, String taskName, String createdBy, Instant createdAt,
                String cancelReason, String canceledBy, Instant canceledAt) {
            this.taskId = taskId; this.taskName = taskName; this.createdBy = createdBy;
            this.createdAt = createdAt; this.cancelReason = cancelReason;
            this.canceledBy = canceledBy; this.canceledAt = canceledAt;
        }
        public String getTaskId() { return taskId; }
        public String getTaskName() { return taskName; }
        public String getCreatedBy() { return createdBy; }
        public Instant getCreatedAt() { return createdAt; }
        public String getCancelReason() { return cancelReason; }
        public String getCanceledBy() { return canceledBy; }
        public Instant getCanceledAt() { return canceledAt; }
    }

    public static class SubmissionSummary {
        @Id private String submissionId;
        private String taskId;
        private int submissionNo;
        private String sourceReviewRoundId;
        private String submittedBy;
        private Instant submittedAt;
        public SubmissionSummary() { }
        public SubmissionSummary(
                String submissionId, String taskId, int submissionNo, String sourceReviewRoundId,
                String submittedBy, Instant submittedAt) {
            this.submissionId = submissionId; this.taskId = taskId; this.submissionNo = submissionNo;
            this.sourceReviewRoundId = sourceReviewRoundId; this.submittedBy = submittedBy;
            this.submittedAt = submittedAt;
        }
        public String getSubmissionId() { return submissionId; }
        public String getTaskId() { return taskId; }
        public int getSubmissionNo() { return submissionNo; }
        public String getSourceReviewRoundId() { return sourceReviewRoundId; }
        public String getSubmittedBy() { return submittedBy; }
        public Instant getSubmittedAt() { return submittedAt; }
    }

    public static class ReviewSummary {
        @Id private String reviewRoundId;
        private String taskId;
        private int roundNo;
        private String reviewerId;
        private Map<String, ReviewIssue> issues;
        private Instant startedAt;
        private Instant completedAt;
        public ReviewSummary() { }
        public ReviewSummary(
                String reviewRoundId, String taskId, int roundNo, String reviewerId,
                Map<String, ReviewIssue> issues, Instant startedAt, Instant completedAt) {
            this.reviewRoundId = reviewRoundId; this.taskId = taskId; this.roundNo = roundNo;
            this.reviewerId = reviewerId; this.issues = issues;
            this.startedAt = startedAt; this.completedAt = completedAt;
        }
        public String getReviewRoundId() { return reviewRoundId; }
        public String getTaskId() { return taskId; }
        public int getRoundNo() { return roundNo; }
        public String getReviewerId() { return reviewerId; }
        public Map<String, ReviewIssue> getIssues() { return issues == null ? Map.of() : Map.copyOf(issues); }
        public Instant getStartedAt() { return startedAt; }
        public Instant getCompletedAt() { return completedAt; }
    }
}
