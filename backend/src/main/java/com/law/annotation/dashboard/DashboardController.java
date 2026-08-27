package com.law.annotation.dashboard;

import com.law.annotation.common.response.ApiResponse;
import com.law.annotation.dashboard.dto.DashboardSummaryResponse;
import com.law.annotation.dashboard.dto.DashboardTodoResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@PreAuthorize("hasRole('ADMIN')")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public ApiResponse<DashboardSummaryResponse> summary() {
        return ApiResponse.success(dashboardService.getSummary());
    }

    @GetMapping("/todos")
    public ApiResponse<DashboardTodoResponse> todos() {
        return ApiResponse.success(dashboardService.getTodos());
    }
}
