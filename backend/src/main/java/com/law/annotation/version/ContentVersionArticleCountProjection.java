package com.law.annotation.version;

import org.springframework.data.annotation.Id;

/** Database-computed article count for one content version. */
public record ContentVersionArticleCountProjection(
        @Id String id,
        String lawId,
        int articleCount) {

    public String getId() {
        return id;
    }

    public String getLawId() {
        return lawId;
    }

    public int getArticleCount() {
        return articleCount;
    }
}
