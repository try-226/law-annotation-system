package com.law.annotation.law;

import java.util.UUID;

public class ArticleSnapshot {

    private final String articleId;
    private final String number;
    private final String body;
    private final int order;

    public ArticleSnapshot(String articleId, String number, String body, int order) {
        this.articleId = LawDomainRules.requireIdentifier(articleId, "articleId");
        this.number = LawDomainRules.validateArticleNumber(number);
        this.body = LawDomainRules.validateArticleBody(body);
        if (order < 0) {
            throw new IllegalArgumentException("条文顺序不能小于0");
        }
        this.order = order;
    }

    public static ArticleSnapshot createNew(String number, String body, int order) {
        return new ArticleSnapshot(UUID.randomUUID().toString(), number, body, order);
    }

    public static ArticleSnapshot carryForward(
            String articleId,
            String number,
            String body,
            int order) {
        return new ArticleSnapshot(articleId, number, body, order);
    }

    public String getArticleId() {
        return articleId;
    }

    public String getNumber() {
        return number;
    }

    public String getBody() {
        return body;
    }

    public int getOrder() {
        return order;
    }
}
