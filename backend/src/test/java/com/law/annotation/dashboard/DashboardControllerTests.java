package com.law.annotation.dashboard;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.law.annotation.auth.ActiveUserFilter;
import com.law.annotation.auth.AuthErrorCodes;
import com.law.annotation.auth.MongoUserDetailsService;
import com.law.annotation.auth.RestSecurityErrorHandler;
import com.law.annotation.auth.SecurityConfig;
import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.common.exception.GlobalExceptionHandler;
import com.law.annotation.dashboard.dto.DashboardSummaryResponse;
import com.law.annotation.dashboard.dto.DashboardTodoItemResponse;
import com.law.annotation.dashboard.dto.DashboardTodoResponse;
import com.law.annotation.user.UserDocument;
import com.law.annotation.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DashboardController.class)
@Import({
        SecurityConfig.class,
        RestSecurityErrorHandler.class,
        ActiveUserFilter.class,
        GlobalExceptionHandler.class
})
class DashboardControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private MongoUserDetailsService mongoUserDetailsService;

    @Test
    void adminCanReadSummaryAndTodos() throws Exception {
        UserDocument admin = activeUser("admin", Role.ADMIN);
        UserPrincipal principal = UserPrincipal.from(admin);
        when(userRepository.findById("admin")).thenReturn(Optional.of(admin));
        when(dashboardService.getSummary())
                .thenReturn(new DashboardSummaryResponse(2, 7, 1, 3, 2, 1, 0, 1));
        when(dashboardService.getTodos()).thenReturn(new DashboardTodoResponse(
                List.of(new DashboardTodoItemResponse(
                        "task-review",
                        "待审核任务",
                        TaskType.ORDINARY,
                        "law-1",
                        "测试法",
                        TaskState.PENDING_REVIEW,
                        Instant.parse("2026-08-27T00:00:00Z"))),
                List.of()));

        mockMvc.perform(get("/dashboard/summary").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalLaws").value(2))
                .andExpect(jsonPath("$.data.totalArticles").value(7))
                .andExpect(jsonPath("$.data.pendingReviewTasks").value(2));
        mockMvc.perform(get("/dashboard/todos").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingReview[0].taskId")
                        .value("task-review"))
                .andExpect(jsonPath("$.data.pendingReview[0].taskState")
                        .value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.pendingRereview").isArray());
    }

    @Test
    void annotatorCannotReadDashboard() throws Exception {
        UserDocument annotator = activeUser("annotator", Role.ANNOTATOR);
        when(userRepository.findById("annotator")).thenReturn(Optional.of(annotator));

        mockMvc.perform(get("/dashboard/summary")
                        .with(user(UserPrincipal.from(annotator))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(AuthErrorCodes.FORBIDDEN));
        mockMvc.perform(get("/dashboard/todos")
                        .with(user(UserPrincipal.from(annotator))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(AuthErrorCodes.FORBIDDEN));

        verifyNoInteractions(dashboardService);
    }

    @Test
    void anonymousDashboardRequestUsesUnifiedAuthenticationError() throws Exception {
        mockMvc.perform(get("/dashboard/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(AuthErrorCodes.UNAUTHENTICATED));

        verifyNoInteractions(dashboardService);
    }

    private static UserDocument activeUser(String id, Role role) {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        UserDocument user = new UserDocument(
                role == Role.ADMIN ? "管理员" : "标注员",
                id,
                id,
                "$2a$12$hash",
                role,
                true,
                now,
                now);
        user.setId(id);
        return user;
    }
}
