package com.law.annotation.export;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.law.annotation.auth.ActiveUserFilter;
import com.law.annotation.auth.MongoUserDetailsService;
import com.law.annotation.auth.RestSecurityErrorHandler;
import com.law.annotation.auth.SecurityConfig;
import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.exception.GlobalExceptionHandler;
import com.law.annotation.export.dto.LawExportRequest;
import com.law.annotation.user.UserDocument;
import com.law.annotation.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ExportController.class)
@Import({
        SecurityConfig.class,
        RestSecurityErrorHandler.class,
        ActiveUserFilter.class,
        GlobalExceptionHandler.class
})
class ExportControllerTests {

    private static final Instant NOW = Instant.parse("2026-08-26T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExportService exportService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private MongoUserDetailsService mongoUserDetailsService;

    private UserPrincipal admin;
    private UserPrincipal annotator;

    @BeforeEach
    void setUp() {
        UserDocument adminUser = activeUser("admin-1", Role.ADMIN);
        UserDocument annotatorUser = activeUser("annotator-1", Role.ANNOTATOR);
        admin = UserPrincipal.from(adminUser);
        annotator = UserPrincipal.from(annotatorUser);
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(adminUser));
        when(userRepository.findById("annotator-1")).thenReturn(Optional.of(annotatorUser));
    }

    @Test
    void adminDownloadsCsvWithExpectedHeadersAndUtf8Content() throws Exception {
        byte[] csv = "lawId,articleId\r\nlaw-1,article-1\r\n"
                .getBytes(StandardCharsets.UTF_8);
        when(exportService.export(
                org.mockito.ArgumentMatchers.eq("law-1"),
                org.mockito.ArgumentMatchers.any(LawExportRequest.class)))
                .thenReturn(new ExportedFile(
                        csv,
                        MediaType.parseMediaType("text/csv;charset=UTF-8"),
                        "law-law-1-plain.csv"));

        mockMvc.perform(post("/laws/law-1/export")
                        .with(user(admin))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wholeRequest("CSV")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        containsString("attachment")))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        containsString("law-law-1-plain.csv")))
                .andExpect(content().bytes(csv));
    }

    @Test
    void adminDownloadsJsonAsApplicationJson() throws Exception {
        String jsonText = "{\"articles\":[{\"body\":\"中文正文\"}]}";
        byte[] json = jsonText.getBytes(StandardCharsets.UTF_8);
        when(exportService.export(
                org.mockito.ArgumentMatchers.eq("law-1"),
                org.mockito.ArgumentMatchers.any(LawExportRequest.class)))
                .thenReturn(new ExportedFile(
                        json,
                        MediaType.parseMediaType("application/json;charset=UTF-8"),
                        "law-law-1-formal.json"));

        mockMvc.perform(post("/laws/law-1/export")
                        .with(user(admin))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scope":"WHOLE","articleIds":[],"type":"FORMAL","format":"JSON"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(content().encoding(StandardCharsets.UTF_8))
                .andExpect(content().bytes(json))
                .andExpect(content().string(jsonText));
    }

    @Test
    void annotatorCannotExportLaws() throws Exception {
        mockMvc.perform(post("/laws/law-1/export")
                        .with(user(annotator))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wholeRequest("JSON")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH.FORBIDDEN"));
        verify(exportService, never()).export(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(post("/laws/law-1/export")
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wholeRequest("JSON")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH.UNAUTHENTICATED"));
    }

    @Test
    void exportRequiresCsrfForAdmin() throws Exception {
        mockMvc.perform(post("/laws/law-1/export")
                        .with(user(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wholeRequest("JSON")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH.CSRF_INVALID"));
    }

    @Test
    void missingScopeUsesUnifiedValidationEnvelope() throws Exception {
        mockMvc.perform(post("/laws/law-1/export")
                        .with(user(admin))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"articleIds":[],"type":"PLAIN","format":"JSON"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON.VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.locators[0].path").value("scope"));
    }

    private static String wholeRequest(String format) {
        return """
                {"scope":"WHOLE","articleIds":[],"type":"PLAIN","format":"%s"}
                """.formatted(format);
    }

    private static UserDocument activeUser(String id, Role role) {
        UserDocument user = new UserDocument(
                "测试用户", id, id, "$2a$12$hash", role, true, NOW, NOW);
        user.setId(id);
        return user;
    }
}
