package com.law.annotation.dashboard.dto;

import java.util.List;

public record DashboardTodoResponse(
        List<DashboardTodoItemResponse> pendingReview,
        List<DashboardTodoItemResponse> pendingRereview) {

    public DashboardTodoResponse {
        pendingReview = List.copyOf(pendingReview);
        pendingRereview = List.copyOf(pendingRereview);
    }
}
