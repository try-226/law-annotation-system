package com.law.annotation.user;

public final class UserErrorCodes {

    public static final String NOT_FOUND = "USER.NOT_FOUND";
    public static final String ACCOUNT_ALREADY_EXISTS = "USER.ACCOUNT_ALREADY_EXISTS";
    public static final String SELF_ACTION_FORBIDDEN = "USER.SELF_ACTION_FORBIDDEN";
    public static final String LAST_ENABLED_ADMIN = "USER.LAST_ENABLED_ADMIN";
    public static final String ACTIVE_TASK_EXISTS = "USER.ACTIVE_TASK_EXISTS";
    public static final String UNFINISHED_REVIEW_EXISTS = "USER.UNFINISHED_REVIEW_EXISTS";
    public static final String BUSINESS_HISTORY_EXISTS = "USER.BUSINESS_HISTORY_EXISTS";

    private UserErrorCodes() {
    }
}
