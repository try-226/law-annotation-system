package com.law.annotation.search;

public enum SearchScope {
    ALL,
    LAW_TEXT,
    ANNOTATION;

    boolean includesLawText() {
        return this == ALL || this == LAW_TEXT;
    }

    boolean includesAnnotation() {
        return this == ALL || this == ANNOTATION;
    }
}
