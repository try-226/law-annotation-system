package com.law.annotation.law;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.law.annotation.auth.ActiveUserFilter;
import com.law.annotation.auth.MongoUserDetailsService;
import com.law.annotation.auth.RestSecurityErrorHandler;
import com.law.annotation.auth.SecurityConfig;
import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.common.exception.GlobalExceptionHandler;
import com.law.annotation.common.response.PageResponse;
import com.law.annotation.law.dto.LawDetailViewResponse;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = LawController.class)
@Import({
        SecurityConfig.class,
        RestSecurityErrorHandler.class,
        ActiveUserFilter.class,
        GlobalExceptionHandler.class
})
class LawControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LawImportService lawImportService;

    @MockitoBean
    private LawQueryService lawQueryService;

    @MockitoBean
    private LawMaintenanceService lawMaintenanceService;

    @MockitoBean
    private LawRecycleService lawRecycleService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private MongoUserDetailsService mongoUserDetailsService;

    @Test
    void annotatorCannotAccessLawManagement() throws Exception {
        UserDocument annotator = activeUser("annotator", Role.ANNOTATOR);
        when(userRepository.findById("annotator")).thenReturn(Optional.of(annotator));

        mockMvc.perform(get("/laws").with(user(UserPrincipal.from(annotator))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH.FORBIDDEN"));
        mockMvc.perform(get("/laws/law-1").with(user(UserPrincipal.from(annotator))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH.FORBIDDEN"));
    }

    @Test
    void adminCanAccessLawList() throws Exception {
        UserDocument admin = activeUser("admin", Role.ADMIN);
        when(userRepository.findById("admin")).thenReturn(Optional.of(admin));
        when(lawQueryService.list(null, 0, 10))
                .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0));

        mockMvc.perform(get("/laws").with(user(UserPrincipal.from(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void adminCanAccessLawDetail() throws Exception {
        UserDocument admin = activeUser("admin", Role.ADMIN);
        when(userRepository.findById("admin")).thenReturn(Optional.of(admin));
        Instant now = Instant.parse("2026-08-19T00:00:00Z");
        when(lawQueryService.getViewDetail("law-1")).thenReturn(new LawDetailViewResponse(
                "law-1",
                "测试法",
                "制定机关",
                LocalDate.of(2026, 8, 19),
                ValidityStatus.ACTIVE,
                now,
                List.of(),
                List.of(),
                null,
                false,
                false,
                null,
                "content-1",
                1,
                new LawDetailViewResponse.ContentVersionReference("content-1", 1, now),
                false,
                now));

        mockMvc.perform(get("/laws/law-1").with(user(UserPrincipal.from(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("law-1"))
                .andExpect(jsonPath("$.data.currentContentVersionId").value("content-1"))
                .andExpect(jsonPath("$.data.currentContentVersionSeq").value(1))
                .andExpect(jsonPath("$.data.currentContentVersion.seq").value(1));
    }

    @Test
    void adminCanAccessRecycleList() throws Exception {
        UserDocument admin = activeUser("admin", Role.ADMIN);
        when(userRepository.findById("admin")).thenReturn(Optional.of(admin));
        when(lawQueryService.listRecycle(null, 0, 10))
                .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0));

        mockMvc.perform(get("/laws/recycle").with(user(UserPrincipal.from(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void annotatorCannotDeleteOrRestoreLaw() throws Exception {
        UserDocument annotator = activeUser("annotator", Role.ANNOTATOR);
        when(userRepository.findById("annotator")).thenReturn(Optional.of(annotator));
        mockMvc.perform(delete("/laws/law-1")
                        .with(user(UserPrincipal.from(annotator)))
                        .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/laws/law-1/restore")
                        .with(user(UserPrincipal.from(annotator)))
                        .with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    void corsPreflightRejectsRemovedWholeLawPut() throws Exception {
        mockMvc.perform(options("/laws/law-1")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteAndRestoreRequireCsrfForAdmin() throws Exception {
        UserDocument admin = activeUser("admin", Role.ADMIN);
        when(userRepository.findById("admin")).thenReturn(Optional.of(admin));

        mockMvc.perform(delete("/laws/law-1")
                        .with(user(UserPrincipal.from(admin))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/laws/law-1/restore")
                        .with(user(UserPrincipal.from(admin))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/laws/law-1")
                        .with(user(UserPrincipal.from(admin)))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/laws/law-1/restore")
                        .with(user(UserPrincipal.from(admin)))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk());
        verify(lawRecycleService).deleteLaw("law-1");
        verify(lawRecycleService).restoreLaw("law-1");
    }

    @Test
    void parseMutationRequiresCsrfForAdmin() throws Exception {
        UserDocument admin = activeUser("admin", Role.ADMIN);
        when(userRepository.findById("admin")).thenReturn(Optional.of(admin));

        mockMvc.perform(post("/laws/import/parse")
                        .with(user(UserPrincipal.from(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullTextPaste\":\"测试法\\n第一条 正文\"}"))
                .andExpect(status().isForbidden());

        when(lawImportService.parse(any())).thenReturn(new com.law.annotation.law.dto.LawImportPreviewResponse(
                new com.law.annotation.law.dto.LawBaseInfoInput(null, null, null, null),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
        mockMvc.perform(post("/laws/import/parse")
                        .with(user(UserPrincipal.from(admin)))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullTextPaste\":\"测试法\\n第一条 正文\"}"))
                .andExpect(status().isOk());
    }

    private static UserDocument activeUser(String id, Role role) {
        Instant now = Instant.now();
        UserDocument user = new UserDocument(
                "测试用户", id, id, "$2a$12$hash", role, true, now, now);
        user.setId(id);
        return user;
    }
}
