package com.law.annotation.user;

public interface UserBusinessUsagePort {

    boolean hasActiveTask(String userId);

    boolean hasUnfinishedReviewRound(String userId);

    boolean hasBusinessHistory(String userId);
}
