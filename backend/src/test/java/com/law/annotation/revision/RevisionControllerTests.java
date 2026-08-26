package com.law.annotation.revision;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.common.exception.GlobalExceptionHandler;
import com.law.annotation.field.FieldConfigSnapshot;
import com.law.annotation.revision.dto.CreateRevisionTaskRequest;
import com.law.annotation.task.TaskContentVersionSnapshot;
import com.law.annotation.task.TaskLawBaseInfoSnapshot;
import com.law.annotation.task.dto.TaskDetailResponse;
import com.law.annotation.user.UserDocument;
import com.law.annotation.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = RevisionController.class)
@Import({
        SecurityConfig.class,
        RestSecurityErrorHandler.class,
        ActiveUserFilter.class,
        GlobalExceptionHandler.class
})
class RevisionControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RevisionService revisionService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private MongoUserDetailsService mongoUserDetailsService;

    @Test
    void adminCreatesRevisionThroughDedicatedEndpoint() throws Exception {
        UserDocument admin = activeUser("admin-1", Role.ADMIN);
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));
        when(revisionService.create(any(CreateRevisionTaskRequest.class), any()))
                .thenReturn(detail());

        mockMvc.perform(post("/tasks/revision")
                        .with(user(UserPrincipal.from(admin)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lawId":"law-1",
                                  "annotatorId":"annotator-1",
                                  "overall":true,
                                  "articleIds":[]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.taskType").value("REVISION"))
                .andExpect(jsonPath("$.data.revisionScope.mode")
                        .value("ANNOTATION_ONLY"));
    }

    @Test
    void clientOwnedTaskStateAndModeFieldsAreRejected() throws Exception {
        UserDocument admin = activeUser("admin-1", Role.ADMIN);
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));

        mockMvc.perform(post("/tasks/revision")
                        .with(user(UserPrincipal.from(admin)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lawId":"law-1",
                                  "annotatorId":"annotator-1",
                                  "taskState":"APPROVED",
                                  "mode":"ANNOTATION_ONLY"
                                }
                                """))
                .andExpect(status().isBadRequest());
        verify(revisionService, never()).create(any(), any());
    }

    private static TaskDetailResponse detail() {
        Instant now = Instant.parse("2026-08-26T00:00:00Z");
        RevisionScope scope = new RevisionScope(
                RevisionMode.ANNOTATION_ONLY, true, List.of(), List.of());
        return new TaskDetailResponse(
                "task-1", TaskType.REVISION, TaskState.PENDING_ANNOTATION,
                "law-1", "annotator-1", "标注员", "修订任务", null,
                "content-1", new TaskContentVersionSnapshot("content-1", 1, List.of()),
                new TaskLawBaseInfoSnapshot(
                        "测试法", "制定机关", LocalDate.of(2026, 8, 26),
                        ValidityStatus.ACTIVE),
                List.of(), new FieldConfigSnapshot(List.of(), List.of()),
                "annotation-1", scope, "admin-1", null, null, null,
                now, now);
    }

    private static UserDocument activeUser(String id, Role role) {
        Instant now = Instant.parse("2026-08-26T00:00:00Z");
        UserDocument user = new UserDocument(
                id, id, id, "$2a$12$hash", role, true, now, now);
        user.setId(id);
        return user;
    }
}
