package com.law.annotation.review;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.law.annotation.auth.ActiveUserFilter;
import com.law.annotation.auth.MongoUserDetailsService;
import com.law.annotation.auth.RestSecurityErrorHandler;
import com.law.annotation.auth.SecurityConfig;
import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.exception.GlobalExceptionHandler;
import com.law.annotation.review.dto.ReviewDetailResponse;
import com.law.annotation.review.dto.ReviewProgressResponse;
import com.law.annotation.user.UserDocument;
import com.law.annotation.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ReviewController.class)
@Import({
        SecurityConfig.class,
        RestSecurityErrorHandler.class,
        ActiveUserFilter.class,
        GlobalExceptionHandler.class
})
class ReviewControllerTests {

    private static final Instant NOW = Instant.parse("2026-08-26T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private MongoUserDetailsService mongoUserDetailsService;

    private UserPrincipal admin;

    @BeforeEach
    void setUp() {
        UserDocument user = new UserDocument(
                "管理员",
                "admin-1",
                "admin-1",
                "$2a$12$hash",
                Role.ADMIN,
                true,
                NOW,
                NOW);
        user.setId("admin-1");
        admin = UserPrincipal.from(user);
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(user));
    }

    @Test
    void startAndReadExposeReviewDetail() throws Exception {
        when(reviewService.start("task-1", admin)).thenReturn(detail());
        when(reviewService.getReview("task-1", admin)).thenReturn(detail());

        mockMvc.perform(post("/tasks/task-1/review/start")
                        .with(user(admin))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roundType").value("INITIAL_REVIEW"))
                .andExpect(jsonPath("$.data.progress.total").value(3))
                .andExpect(jsonPath("$.data.writable").value(true));
        mockMvc.perform(get("/tasks/task-1/review").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewRoundId").value("round-1"));
    }

    @Test
    void itemActionsAndCompleteUseExplicitPostEndpoints() throws Exception {
        when(reviewService.check(
                eq("task-1"), eq("round-1"),
                eq(ReviewItemLocator.article("article-1")), eq(admin)))
                .thenReturn(detail());
        when(reviewService.issue(
                eq("task-1"), eq("round-1"),
                eq(ReviewItemLocator.overall()), eq("请修改"), eq(admin)))
                .thenReturn(detail());
        when(reviewService.complete("task-1", "round-1", admin)).thenReturn(detail());

        mockMvc.perform(post(
                        "/tasks/task-1/review/rounds/round-1/articles/article-1/check")
                        .with(user(admin))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/tasks/task-1/review/rounds/round-1/overall/issue")
                        .with(user(admin))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"请修改"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/tasks/task-1/review/rounds/round-1/complete")
                        .with(user(admin))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk());

        verify(reviewService).issue(
                "task-1", "round-1", ReviewItemLocator.overall(), "请修改", admin);
    }

    @Test
    void writeActionsRequireCsrfAndAuthentication() throws Exception {
        mockMvc.perform(post("/tasks/task-1/review/start").with(user(admin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH.CSRF_INVALID"));
        mockMvc.perform(get("/tasks/task-1/review"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH.UNAUTHENTICATED"));
    }

    private static ReviewDetailResponse detail() {
        return new ReviewDetailResponse(
                "task-1",
                "round-1",
                1,
                ReviewRoundType.INITIAL_REVIEW,
                TaskState.PENDING_REVIEW,
                "admin-1",
                true,
                new ReviewProgressResponse(3, 0, 3, 0),
                List.of(),
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                NOW,
                null,
                null);
    }
}
