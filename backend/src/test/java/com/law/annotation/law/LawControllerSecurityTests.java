package com.law.annotation.law;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import com.law.annotation.common.response.PageResponse;
import com.law.annotation.user.UserDocument;
import com.law.annotation.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
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
    private LawUpdateService lawUpdateService;

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
    void wholeLawUpdateRequiresCsrfAndUsesAuthenticatedOperator() throws Exception {
        UserDocument admin = activeUser("admin", Role.ADMIN);
        when(userRepository.findById("admin")).thenReturn(Optional.of(admin));
        String body = """
                {
                  "baseInfo": {
                    "name": "测试法",
                    "issuingAuthority": "制定机关",
                    "publicationDate": "2026-08-19",
                    "validityStatus": "ACTIVE"
                  },
                  "structure": [],
                  "articles": [
                    {
                      "articleId": "article-1",
                      "clientKey": "article-1",
                      "number": "第一条",
                      "body": "正文",
                      "order": 0
                    }
                  ]
                }
                """;

        mockMvc.perform(put("/laws/law-1")
                        .with(user(UserPrincipal.from(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/laws/law-1")
                        .with(user(UserPrincipal.from(admin)))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        verify(lawUpdateService).updateLaw(anyString(), any(), anyString());
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
