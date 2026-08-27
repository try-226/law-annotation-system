package com.law.annotation.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.law.annotation.auth.ActiveUserFilter;
import com.law.annotation.auth.MongoUserDetailsService;
import com.law.annotation.auth.RestSecurityErrorHandler;
import com.law.annotation.auth.SecurityConfig;
import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.exception.GlobalExceptionHandler;
import com.law.annotation.history.dto.LawHistoryResponse;
import com.law.annotation.user.UserDocument;
import com.law.annotation.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@WebMvcTest(controllers = HistoryController.class)
@Import({
        SecurityConfig.class,
        RestSecurityErrorHandler.class,
        ActiveUserFilter.class,
        GlobalExceptionHandler.class
})
class HistoryControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @MockitoBean
    private HistoryService historyService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private MongoUserDetailsService mongoUserDetailsService;

    @Test
    void adminReadsWholeTimeline() throws Exception {
        UserDocument admin = activeUser("admin-1", Role.ADMIN);
        UserPrincipal principal = UserPrincipal.from(admin);
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));
        when(historyService.getLawHistory("law-1", principal))
                .thenReturn(new LawHistoryResponse("law-1", false, null, List.of()));

        mockMvc.perform(get("/laws/law-1/history").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lawId").value("law-1"));

        verify(historyService).getLawHistory("law-1", principal);
    }

    @Test
    void annotatorCannotReadWholeTimeline() throws Exception {
        UserDocument annotator = activeUser("annotator-1", Role.ANNOTATOR);
        when(userRepository.findById("annotator-1")).thenReturn(Optional.of(annotator));

        mockMvc.perform(get("/laws/law-1/history").with(user(UserPrincipal.from(annotator))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH.FORBIDDEN"));

        verifyNoInteractions(historyService);
    }

    @Test
    void annotatorTaskHistoryRouteReachesServiceForOwnerIsolation() throws Exception {
        UserDocument annotator = activeUser("annotator-1", Role.ANNOTATOR);
        UserPrincipal principal = UserPrincipal.from(annotator);
        when(userRepository.findById("annotator-1")).thenReturn(Optional.of(annotator));

        mockMvc.perform(get("/laws/law-1/history/tasks/task-1").with(user(principal)))
                .andExpect(status().isOk());

        verify(historyService).getTask("law-1", "task-1", principal);
    }

    @Test
    void historyExposesOnlyReadEndpoints() {
        Set<RequestMethod> methods = handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().getBeanType() == HistoryController.class)
                .flatMap(entry -> entry.getKey().getMethodsCondition().getMethods().stream())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(methods).containsExactly(RequestMethod.GET);
    }

    private static UserDocument activeUser(String id, Role role) {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        UserDocument user = new UserDocument(
                role == Role.ADMIN ? "管理员" : "标注员",
                id, id, "$2a$12$hash", role, true, now, now);
        user.setId(id);
        return user;
    }
}
