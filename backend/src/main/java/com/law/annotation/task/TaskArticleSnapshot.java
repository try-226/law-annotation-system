package com.law.annotation.task;

import com.law.annotation.law.ArticleSnapshot;

public record TaskArticleSnapshot(
        String articleId,
        String number,
        String body,
        int order) {

    public static TaskArticleSnapshot from(ArticleSnapshot article) {
        return new TaskArticleSnapshot(
                article.getArticleId(),
                article.getNumber(),
                article.getBody(),
                article.getOrder());
    }
}
