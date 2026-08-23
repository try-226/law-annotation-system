package com.law.annotation.task;

import com.law.annotation.version.ContentVersionDocument;
import java.util.List;

public record TaskContentVersionSnapshot(
        String contentVersionId,
        int seq,
        List<TaskArticleSnapshot> articles) {

    public TaskContentVersionSnapshot {
        articles = List.copyOf(articles);
    }

    public static TaskContentVersionSnapshot from(ContentVersionDocument version) {
        return new TaskContentVersionSnapshot(
                version.getId(),
                version.getSeq(),
                version.getSemanticArticlesSnapshot().stream()
                        .map(TaskArticleSnapshot::from)
                        .toList());
    }
}
