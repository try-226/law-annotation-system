package com.law.annotation.annotation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.law.annotation.annotation.dto.AnnotationProgressResponse;
import com.law.annotation.annotation.dto.EditableScopeResponse;
import com.law.annotation.annotation.dto.SaveArticleDraftRequest;
import com.law.annotation.annotation.dto.SaveOverallDraftRequest;
import com.law.annotation.annotation.dto.SubmitReviewResponse;
import com.law.annotation.annotation.dto.TaskDraftResponse;
import com.law.annotation.auth.ActiveUserFilter;
import com.law.annotation.auth.MongoUserDetailsService;
import com.law.annotation.auth.RestSecurityErrorHandler;
import com.law.annotation.auth.SecurityConfig;
import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.ItemType;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.exception.GlobalExceptionHandler;
import com.law.annotation.user.UserDocument;
import com.law.annotation.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AnnotationDraftController.class)
@Import({
        SecurityConfig.class,
        RestSecurityErrorHandler.class,
        ActiveUserFilter.class,
        GlobalExceptionHandler.class
})
class AnnotationDraftControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnnotationDraftService service;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private MongoUserDetailsService mongoUserDetailsService;

    private UserDocument annotator;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        annotator = activeAnnotator();
        principal = UserPrincipal.from(annotator);
        when(userRepository.findById("annotator-1")).thenReturn(Optional.of(annotator));
    }

    @Test
    void readsDraftAndProgress() throws Exception {
        when(service.getDraft("task-1", principal)).thenReturn(draftResponse());

        mockMvc.perform(get("/tasks/task-1/draft").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progress.totalArticles").value(2))
                .andExpect(jsonPath("$.data.progress.filledArticles").value(2))
                .andExpect(jsonPath("$.data.editableScope.overallEditable").value(true));
    }

    @Test
    void savesOverallAndArticleDraftsThroughPutEndpoints() throws Exception {
        when(service.saveOverall(
                org.mockito.ArgumentMatchers.eq("task-1"),
                org.mockito.ArgumentMatchers.any(SaveOverallDraftRequest.class),
                org.mockito.ArgumentMatchers.eq(principal)))
                .thenReturn(draftResponse());
        when(service.saveArticle(
                org.mockito.ArgumentMatchers.eq("task-1"),
                org.mockito.ArgumentMatchers.eq("article-1"),
                org.mockito.ArgumentMatchers.any(SaveArticleDraftRequest.class),
                org.mockito.ArgumentMatchers.eq(principal)))
                .thenReturn(draftResponse());

        mockMvc.perform(put("/tasks/task-1/draft/overall")
                        .with(user(principal))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lawCategory":"民事","overallKeywords":"合同"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallDraft.lawCategory").value("民事"));

        mockMvc.perform(put("/tasks/task-1/draft/articles/article-1")
                        .with(user(principal))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemType":"DEFINITION","keywords":"定义"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.articleDrafts.article-1.itemType")
                        .value("DEFINITION"));
    }

    @Test
    void clearEndpointsAndSubmitAreActionsProtectedByCsrf() throws Exception {
        when(service.clearOverall("task-1", principal)).thenReturn(draftResponse());
        when(service.clearArticle("task-1", "article-1", principal)).thenReturn(draftResponse());
        when(service.submitReview("task-1", principal)).thenReturn(new SubmitReviewResponse(
                "task-1", "submission-1", TaskState.PENDING_REVIEW, AnnotationTestFixtures.NOW));
        when(service.submitRereview("task-1", principal)).thenReturn(new SubmitReviewResponse(
                "task-1", "submission-2", TaskState.PENDING_REREVIEW, AnnotationTestFixtures.NOW));

        mockMvc.perform(delete("/tasks/task-1/draft/overall")
                        .with(user(principal))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/tasks/task-1/draft/articles/article-1")
                        .with(user(principal))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/tasks/task-1/submit-review")
                        .with(user(principal))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskState").value("PENDING_REVIEW"));
        mockMvc.perform(post("/tasks/task-1/submit-rereview")
                        .with(user(principal))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskState").value("PENDING_REREVIEW"));

        mockMvc.perform(post("/tasks/task-1/submit-review").with(user(principal)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH.CSRF_INVALID"));
    }

    @Test
    void unsupportedDraftFieldsAreRejectedBeforeService() throws Exception {
        mockMvc.perform(put("/tasks/task-1/draft/overall")
                        .with(user(principal))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lawCategory":"民事","state":"PENDING_REVIEW"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON.VALIDATION_FAILED"));

        verifyNoInteractions(service);
    }

    @Test
    void unauthenticatedUserCannotReadDraft() throws Exception {
        mockMvc.perform(get("/tasks/task-1/draft"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH.UNAUTHENTICATED"));
    }

    @Test
    void corsPreflightAllowsDraftPutMethod() throws Exception {
        mockMvc.perform(options("/tasks/task-1/draft/overall")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                                org.hamcrest.Matchers.containsString("PUT")));
    }

    private static TaskDraftResponse draftResponse() {
        return new TaskDraftResponse(
                "task-1",
                TaskState.ANNOTATING,
                new OverallDraftValues("民事", "合同", null, null),
                Map.of(
                        "article-1",
                        new ArticleDraftValues(ItemType.DEFINITION, "定义", null, null, null)),
                new EditableScopeResponse(true, List.of("article-1", "article-2")),
                new AnnotationProgressResponse(2, 2, true),
                3,
                AnnotationTestFixtures.NOW);
    }

    private static UserDocument activeAnnotator() {
        UserDocument user = new UserDocument(
                "标注员甲",
                "annotator-1",
                "annotator-1",
                "$2a$12$hash",
                Role.ANNOTATOR,
                true,
                AnnotationTestFixtures.NOW,
                AnnotationTestFixtures.NOW);
        user.setId("annotator-1");
        return user;
    }
}
