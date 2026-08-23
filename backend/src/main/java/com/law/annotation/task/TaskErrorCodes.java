package com.law.annotation.task;

public final class TaskErrorCodes {

    public static final String NOT_FOUND = "TASK.NOT_FOUND";
    public static final String LAW_NOT_FOUND = "TASK.LAW_NOT_FOUND";
    public static final String LAW_DELETED = "TASK.LAW_DELETED";
    public static final String FORMAL_ANNOTATION_EXISTS = "TASK.FORMAL_ANNOTATION_EXISTS";
    public static final String ACTIVE_TASK_EXISTS = "TASK.ACTIVE_TASK_EXISTS";
    public static final String CONTENT_VERSION_INVALID = "TASK.CONTENT_VERSION_INVALID";
    public static final String NO_VALID_ARTICLE = "TASK.NO_VALID_ARTICLE";
    public static final String ANNOTATOR_NOT_FOUND = "TASK.ANNOTATOR_NOT_FOUND";
    public static final String ANNOTATOR_DISABLED = "TASK.ANNOTATOR_DISABLED";
    public static final String ANNOTATOR_ROLE_INVALID = "TASK.ANNOTATOR_ROLE_INVALID";
    public static final String INVALID_STATE_TRANSITION = "TASK.INVALID_STATE_TRANSITION";
    public static final String NOT_ASSIGNEE = "TASK.NOT_ASSIGNEE";

    private TaskErrorCodes() {
    }
}
