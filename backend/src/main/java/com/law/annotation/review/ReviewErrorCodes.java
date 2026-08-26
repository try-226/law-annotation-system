package com.law.annotation.review;

public final class ReviewErrorCodes {

    public static final String NOT_FOUND = "REVIEW.NOT_FOUND";
    public static final String NOT_STARTED = "REVIEW.NOT_STARTED";
    public static final String ALREADY_ASSIGNED = "REVIEW.ALREADY_ASSIGNED";
    public static final String NOT_REVIEWER = "REVIEW.NOT_REVIEWER";
    public static final String INVALID_TASK_STATE = "REVIEW.INVALID_TASK_STATE";
    public static final String ITEM_NOT_IN_SCOPE = "REVIEW.ITEM_NOT_IN_SCOPE";
    public static final String ISSUE_REASON_INVALID = "REVIEW.ISSUE_REASON_INVALID";
    public static final String INCOMPLETE = "REVIEW.INCOMPLETE";
    public static final String ALREADY_COMPLETED = "REVIEW.ALREADY_COMPLETED";
    public static final String SOURCE_INVALID = "REVIEW.SOURCE_INVALID";
    public static final String COMPLETION_CONFLICT = "REVIEW.COMPLETION_CONFLICT";

    private ReviewErrorCodes() {
    }
}
