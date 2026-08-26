package com.law.annotation.revision;

public final class RevisionErrorCodes {

    public static final String CURRENT_ANNOTATION_REQUIRED =
            "REVISION.CURRENT_ANNOTATION_REQUIRED";
    public static final String BASE_ANNOTATION_INVALID =
            "REVISION.BASE_ANNOTATION_INVALID";
    public static final String SCOPE_EMPTY = "REVISION.SCOPE_EMPTY";
    public static final String ARTICLE_NOT_IN_LATEST_CONTENT =
            "REVISION.ARTICLE_NOT_IN_LATEST_CONTENT";
    public static final String DELETED_ARTICLE_REQUESTED =
            "REVISION.DELETED_ARTICLE_REQUESTED";
    public static final String CONTENT_CHANGE_SCOPE_INVALID =
            "REVISION.CONTENT_CHANGE_SCOPE_INVALID";
    public static final String WRITE_OUTSIDE_SCOPE =
            "REVISION.WRITE_OUTSIDE_SCOPE";
    public static final String BASIS_CHANGED = "REVISION.BASIS_CHANGED";

    private RevisionErrorCodes() {
    }
}
