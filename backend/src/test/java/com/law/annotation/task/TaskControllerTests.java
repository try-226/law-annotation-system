package com.law.annotation.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.common.exception.GlobalExceptionHandler;
import com.law.annotation.common.response.PageResponse;
import com.law.annotation.field.FieldConfigSnapshot;
import com.law.annotation.task.dto.TaskDetailResponse;
import com.law.annotation.task.dto.TaskListItemResponse;
import com.law.annotation.user.UserDocument;
import com.law.annotation.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@WebMvcTest(controllers = TaskController.class)
@Import({
        SecurityConfig.class,
        RestSecurityErrorHandler.class,
        ActiveUserFilter.class,
        GlobalExceptionHandler.class
})
class TaskControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @MockitoBean
    private TaskService taskService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private MongoUserDetailsService mongoUserDetailsService;

    @Test
    void adminCreatesWholeLawOrdinaryTask() throws Exception {
        UserDocument admin = activeUser("admin-1", Role.ADMIN);
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));
        when(taskService.createOrdinaryTask(
                "law-1", "annotator-1", "任务一", "备注", "admin-1"))
                .thenReturn(detail(TaskState.PENDING_ANNOTATION));

        mockMvc.perform(post("/tasks")
                        .with(user(UserPrincipal.from(admin)))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lawId":"law-1",
                                  "annotatorId":"annotator-1",
                                  "taskName":"任务一",
                                  "remark":"备注"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.taskType").value("ORDINARY"))
                .andExpect(jsonPath("$.data.taskState").value("PENDING_ANNOTATION"));
    }

    @Test
    void createRequestRejectsArticleIds() throws Exception {
        UserDocument admin = activeUser("admin-1", Role.ADMIN);
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));

        mockMvc.perform(post("/tasks")
                        .with(user(UserPrincipal.from(admin)))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lawId":"law-1",
                                  "annotatorId":"annotator-1",
                                  "articleIds":["article-1"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON.VALIDATION_FAILED"));

        verifyNoInteractions(taskService);
    }

    @Test
    void annotatorStartsAssignedTaskThroughActionEndpoint() throws Exception {
        UserDocument annotator = activeUser("annotator-1", Role.ANNOTATOR);
        when(userRepository.findById("annotator-1")).thenReturn(Optional.of(annotator));
        when(taskService.start("task-1", "annotator-1"))
                .thenReturn(detail(TaskState.ANNOTATING));

        mockMvc.perform(post("/tasks/task-1/start")
                        .with(user(UserPrincipal.from(annotator)))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskState").value("ANNOTATING"));

        verify(taskService).start("task-1", "annotator-1");
    }

    @Test
    void adminCancelsTaskWithReason() throws Exception {
        UserDocument admin = activeUser("admin-1", Role.ADMIN);
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));
        when(taskService.cancel("task-1", "取消原因", "admin-1"))
                .thenReturn(detail(TaskState.CANCELED));

        mockMvc.perform(post("/tasks/task-1/cancel")
                        .with(user(UserPrincipal.from(admin)))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"取消原因\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskState").value("CANCELED"));
    }

    @Test
    void roleRulesProtectAdminAndAnnotatorActions() throws Exception {
        UserDocument annotator = activeUser("annotator-1", Role.ANNOTATOR);
        when(userRepository.findById("annotator-1")).thenReturn(Optional.of(annotator));

        mockMvc.perform(post("/tasks")
                        .with(user(UserPrincipal.from(annotator)))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lawId\":\"law-1\",\"annotatorId\":\"annotator-1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH.FORBIDDEN"));

        verify(taskService, never()).createOrdinaryTask(any(), any(), any(), any(), any());
    }

    @Test
    void adminListsAndReadsTaskDetails() throws Exception {
        UserDocument admin = activeUser("admin-1", Role.ADMIN);
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));
        TaskListItemResponse item = new TaskListItemResponse(
                "task-1", "任务一", TaskType.ORDINARY, "law-1", "测试法",
                "annotator-1", "标注员", TaskState.PENDING_ANNOTATION, "备注", Instant.now());
        when(taskService.list(null, null, null, null, null, 0, 20))
                .thenReturn(new PageResponse<>(List.of(item), 0, 20, 1, 1));
        when(taskService.getDetail("task-1")).thenReturn(detail(TaskState.PENDING_ANNOTATION));

        mockMvc.perform(get("/tasks").with(user(UserPrincipal.from(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].lawName").value("测试法"));
        mockMvc.perform(get("/tasks/task-1").with(user(UserPrincipal.from(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentVersionId").value("content-1"));
    }

    @Test
    void controllerExposesNoGenericStateMutationEndpoint() {
        Set<RequestMethod> methods = handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().getBeanType() == TaskController.class)
                .flatMap(entry -> entry.getKey().getMethodsCondition().getMethods().stream())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(methods).containsExactlyInAnyOrder(RequestMethod.GET, RequestMethod.POST);
    }

    private static UserDocument activeUser(String id, Role role) {
        Instant now = Instant.now();
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

    private static TaskDetailResponse detail(TaskState state) {
        Instant now = Instant.parse("2026-08-23T00:00:00Z");
        return new TaskDetailResponse(
                "task-1",
                TaskType.ORDINARY,
                state,
                "law-1",
                "annotator-1",
                "标注员",
                "任务一",
                "备注",
                "content-1",
                new TaskContentVersionSnapshot("content-1", 1, List.of()),
                new TaskLawBaseInfoSnapshot(
                        "测试法", "制定机关", LocalDate.of(2026, 8, 23), ValidityStatus.ACTIVE),
                List.of(),
                new FieldConfigSnapshot(List.of(), List.of()),
                "admin-1",
                null,
                null,
                null,
                now,
                now);
    }
}
