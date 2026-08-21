package com.law.annotation.law;

import java.util.UUID;

public record NewArticleDraft(String articleId, String number, String body, int order) {

    public NewArticleDraft(String number, String body, int order) {
        this(UUID.randomUUID().toString(), number, body, order);
    }

    public NewArticleDraft {
        LawDomainRules.requireIdentifier(articleId, "articleId");
    }
}
