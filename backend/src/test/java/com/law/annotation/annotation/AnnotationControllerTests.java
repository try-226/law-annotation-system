package com.law.annotation.annotation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.law.annotation.auth.ActiveUserFilter;
import com.law.annotation.auth.MongoUserDetailsService;
import com.law.annotation.auth.RestSecurityErrorHandler;
import com.law.annotation.auth.SecurityConfig;
import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.exception.GlobalExceptionHandler;
import com.law.annotation.user.UserDocument;
import com.law.annotation.user.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AnnotationController.class)
@Import({
        SecurityConfig.class,
        RestSecurityErrorHandler.class,
        ActiveUserFilter.class,
        GlobalExceptionHandler.class
})
class AnnotationControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnnotationDraftService annotationDraftService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private MongoUserDetailsService mongoUserDetailsService;

    @Test
    void annotatorCanUseWorkbenchDraftAndSubmitActionEndpoints() throws Exception {
        UserDocument annotator = activeUser("annotator-1", Role.ANNOTATOR);
        UserPrincipal principal = UserPrincipal.from(annotator);
        whenActive(annotator);

        mockMvc.perform(get("/tasks/task-1/annotation").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(put("/tasks/task-1/draft/overall")
                        .with(user(principal))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lawCategory\":\"行政\",\"overallKeywords\":\"监管\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(put("/tasks/task-1/draft/articles/article-1")
                        .with(user(principal))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemType\":\"LIABILITY\",\"keywords\":\"罚款\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(post("/tasks/task-1/submit-review")
                        .with(user(principal))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(annotationDraftService).getWorkbench("task-1", principal);
        verify(annotationDraftService).submitReview("task-1", principal);
    }

    @Test
    void administratorCannotUseAnnotationEndpoints() throws Exception {
        UserDocument admin = activeUser("admin-1", Role.ADMIN);
        whenActive(admin);

        mockMvc.perform(get("/tasks/task-1/annotation")
                        .with(user(UserPrincipal.from(admin))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH.FORBIDDEN"));

        verifyNoInteractions(annotationDraftService);
    }

    private void whenActive(UserDocument user) {
        org.mockito.Mockito.when(userRepository.findById(user.getId()))
                .thenReturn(Optional.of(user));
    }

    private static UserDocument activeUser(String id, Role role) {
        Instant now = Instant.parse("2026-08-23T00:00:00Z");
        UserDocument user = new UserDocument(
                role == Role.ADMIN ? "管理员" : "标注员甲",
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
