package com.law.annotation.version;

import com.law.annotation.annotation.ArticleDraftValues;
import com.law.annotation.annotation.OverallDraftValues;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "annotation_versions")
public class AnnotationVersionDocument {

    @Id
    private final String id;
    private final String lawId;
    private final int seq;
    private final String contentVersionId;
    private final OverallDraftValues overallResult;
    private final Map<String, ArticleDraftValues> articleResults;
    private final String sourceTaskId;
    private final String sourceSubmissionId;
    private final String approvedBy;
    private final Instant approvedAt;

    public AnnotationVersionDocument(
            String id,
            String lawId,
            int seq,
            String contentVersionId,
            OverallDraftValues overallResult,
            Map<String, ArticleDraftValues> articleResults,
            String sourceTaskId,
            String sourceSubmissionId,
            String approvedBy,
            Instant approvedAt) {
        this.id = id;
        this.lawId = lawId;
        this.seq = seq;
        this.contentVersionId = contentVersionId;
        this.overallResult = overallResult;
        this.articleResults = new LinkedHashMap<>(articleResults);
        this.sourceTaskId = sourceTaskId;
        this.sourceSubmissionId = sourceSubmissionId;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
    }

    public String getId() { return id; }
    public String getLawId() { return lawId; }
    public int getSeq() { return seq; }
    public String getContentVersionId() { return contentVersionId; }
    public OverallDraftValues getOverallResult() { return overallResult; }
    public Map<String, ArticleDraftValues> getArticleResults() { return Map.copyOf(articleResults); }
    public String getSourceTaskId() { return sourceTaskId; }
    public String getSourceSubmissionId() { return sourceSubmissionId; }
    public String getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
}
