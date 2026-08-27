package com.law.annotation.search;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.common.exception.GlobalExceptionHandler;
import com.law.annotation.common.response.PageResponse;
import com.law.annotation.search.dto.SearchHitResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SearchController.class)
@Import({
        SecurityConfig.class,
        RestSecurityErrorHandler.class,
        ActiveUserFilter.class,
        GlobalExceptionHandler.class
})
class SearchControllerTests {

    private static final Instant NOW = Instant.parse("2026-08-27T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchService searchService;

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
    void adminCanSearchLawsAndResponseUsesPageEnvelope() throws Exception {
        when(searchService.searchLaws("正文", SearchScope.ALL, 0, 10))
                .thenReturn(new PageResponse<>(
                        List.of(new SearchHitResponse(
                                "law-1",
                                "法律一",
                                "article-1",
                                "第一条",
                                List.of("第一章"),
                                SearchHitSource.ARTICLE_BODY,
                                "article.body",
                                "命中正文",
                                2,
                                4)),
                        0,
                        10,
                        1,
                        1));

        mockMvc.perform(get("/laws/search")
                        .with(user(admin))
                        .param("q", "正文"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].lawId").value("law-1"))
                .andExpect(jsonPath("$.data.items[0].hitSource").value("ARTICLE_BODY"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void annotatorCannotUseAdminLawSearch() throws Exception {
        mockMvc.perform(get("/laws/search")
                        .with(user(annotator))
                        .param("q", "正文"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH.FORBIDDEN"));
        verify(searchService, never()).searchLaws(
                any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void annotatorCanSearchOwnTaskButAdminCannotUseTaskSearch() throws Exception {
        when(searchService.searchTask(
                eq("task-1"),
                eq("正文"),
                eq(SearchScope.LAW_TEXT),
                eq(0),
                eq(10),
                any(UserPrincipal.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0));

        mockMvc.perform(get("/tasks/task-1/search")
                        .with(user(annotator))
                        .param("q", "正文")
                        .param("scope", "LAW_TEXT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());

        when(searchService.searchTask(
                eq("task-1"),
                eq("正文"),
                eq(SearchScope.ALL),
                eq(0),
                eq(10),
                any(UserPrincipal.class)))
                .thenThrow(new ApiException(
                        HttpStatus.FORBIDDEN,
                        "AUTH.FORBIDDEN",
                        "无权执行此操作"));
        mockMvc.perform(get("/tasks/task-1/search")
                        .with(user(admin))
                        .param("q", "正文"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH.FORBIDDEN"));
    }

    @Test
    void unauthenticatedSearchIsRejected() throws Exception {
        mockMvc.perform(get("/laws/search").param("q", "正文"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH.UNAUTHENTICATED"));
        mockMvc.perform(get("/tasks/task-1/search").param("q", "正文"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH.UNAUTHENTICATED"));
    }

    private static UserDocument activeUser(String id, Role role) {
        UserDocument user = new UserDocument(
                "测试用户", id, id, "$2a$12$hash", role, true, NOW, NOW);
        user.setId(id);
        return user;
    }
}
