package com.law.annotation.task;

import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.field.FieldConfigSnapshot;
import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "tasks")
public class TaskDocument {

    @Id
    private String taskId;
    private TaskType taskType;
    private TaskState taskState;
    private String lawId;
    private String annotatorId;
    private String annotatorNameSnapshot;
    private String taskName;
    private String remark;
    private String contentVersionId;
    private TaskContentVersionSnapshot contentVersionSnapshot;
    private TaskLawBaseInfoSnapshot lawBaseInfoSnapshot;
    private List<TaskStructureNodeSnapshot> structureSnapshot;
    private FieldConfigSnapshot fieldConfigSnapshot;
    private String createdBy;
    private String initialSubmissionId;
    private String currentSubmissionId;
    private String currentReviewRoundId;
    private String approvedAnnotationVersionId;
    private String cancelReason;
    private String canceledBy;
    private Instant canceledAt;
    private Instant createdAt;
    private Instant updatedAt;

    public TaskDocument() {
    }

    public TaskDocument(
            String taskId,
            TaskType taskType,
            TaskState taskState,
            String lawId,
            String annotatorId,
            String annotatorNameSnapshot,
            String taskName,
            String remark,
            String contentVersionId,
            TaskContentVersionSnapshot contentVersionSnapshot,
            TaskLawBaseInfoSnapshot lawBaseInfoSnapshot,
            List<TaskStructureNodeSnapshot> structureSnapshot,
            FieldConfigSnapshot fieldConfigSnapshot,
            String createdBy,
            String initialSubmissionId,
            String cancelReason,
            String canceledBy,
            Instant canceledAt,
            Instant createdAt,
            Instant updatedAt) {
        this.taskId = taskId;
        this.taskType = taskType;
        this.taskState = taskState;
        this.lawId = lawId;
        this.annotatorId = annotatorId;
        this.annotatorNameSnapshot = annotatorNameSnapshot;
        this.taskName = taskName;
        this.remark = remark;
        this.contentVersionId = contentVersionId;
        this.contentVersionSnapshot = contentVersionSnapshot;
        this.lawBaseInfoSnapshot = lawBaseInfoSnapshot;
        this.structureSnapshot = List.copyOf(structureSnapshot);
        this.fieldConfigSnapshot = fieldConfigSnapshot;
        this.createdBy = createdBy;
        this.initialSubmissionId = initialSubmissionId;
        this.currentSubmissionId = initialSubmissionId;
        this.cancelReason = cancelReason;
        this.canceledBy = canceledBy;
        this.canceledAt = canceledAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getTaskId() {
        return taskId;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public TaskState getTaskState() {
        return taskState;
    }

    public String getLawId() {
        return lawId;
    }

    public String getAnnotatorId() {
        return annotatorId;
    }

    public String getAnnotatorNameSnapshot() {
        return annotatorNameSnapshot;
    }

    public String getTaskName() {
        return taskName;
    }

    public String getRemark() {
        return remark;
    }

    public String getContentVersionId() {
        return contentVersionId;
    }

    public TaskContentVersionSnapshot getContentVersionSnapshot() {
        return contentVersionSnapshot;
    }

    public TaskLawBaseInfoSnapshot getLawBaseInfoSnapshot() {
        return lawBaseInfoSnapshot;
    }

    public List<TaskStructureNodeSnapshot> getStructureSnapshot() {
        return structureSnapshot;
    }

    public FieldConfigSnapshot getFieldConfigSnapshot() {
        return fieldConfigSnapshot;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getInitialSubmissionId() {
        return initialSubmissionId;
    }

    public String getCurrentSubmissionId() {
        return currentSubmissionId;
    }

    public String getCurrentReviewRoundId() {
        return currentReviewRoundId;
    }

    public String getApprovedAnnotationVersionId() {
        return approvedAnnotationVersionId;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public String getCanceledBy() {
        return canceledBy;
    }

    public Instant getCanceledAt() {
        return canceledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
