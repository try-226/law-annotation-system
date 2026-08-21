package com.law.annotation.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.law.annotation.auth.dto.CurrentUserResponse;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.exception.GlobalExceptionHandler;
import com.law.annotation.common.response.PageResponse;
import com.law.annotation.user.UserController;
import com.law.annotation.user.UserDocument;
import com.law.annotation.user.UserRepository;
import com.law.annotation.user.UserService;
import com.law.annotation.user.dto.UserResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = {AuthController.class, UserController.class})
@Import({
        SecurityConfig.class,
        RestSecurityErrorHandler.class,
        ActiveUserFilter.class,
        GlobalExceptionHandler.class
})
class SecurityWebTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private MongoUserDetailsService mongoUserDetailsService;

    @Test
    void csrfEndpointIsPublicAndReturnsToken() throws Exception {
        mockMvc.perform(get("/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    void anonymousRequestReturnsJsonUnauthorizedResponse() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(AuthErrorCodes.UNAUTHENTICATED));
    }

    @Test
    void annotatorCannotAccessUserManagement() throws Exception {
        UserDocument annotator = activeUser("annotator", Role.ANNOTATOR);
        when(userRepository.findById("annotator")).thenReturn(Optional.of(annotator));

        mockMvc.perform(get("/users").with(user(UserPrincipal.from(annotator))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(AuthErrorCodes.FORBIDDEN));
    }

    @Test
    void adminCanAccessUserManagement() throws Exception {
        UserDocument admin = activeUser("admin", Role.ADMIN);
        when(userRepository.findById("admin")).thenReturn(Optional.of(admin));
        when(userService.listUsers(null, null, null, 0, 20))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/users").with(user(UserPrincipal.from(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void mutationRequiresCsrfAndReturnsJsonError() throws Exception {
        UserDocument admin = activeUser("admin", Role.ADMIN);
        when(userRepository.findById("admin")).thenReturn(Optional.of(admin));

        mockMvc.perform(post("/users")
                        .with(user(UserPrincipal.from(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"测试用户","loginAccount":"test.user","initialPassword":"abc123","role":"ANNOTATOR"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(AuthErrorCodes.CSRF_INVALID));

        when(userService.createUser(any(), any(), any(), any()))
                .thenReturn(new UserResponse(
                        "u1", "测试用户", "test.user", Role.ANNOTATOR, true,
                        Instant.now(), Instant.now()));
        mockMvc.perform(post("/users")
                        .with(user(UserPrincipal.from(admin)))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"测试用户","loginAccount":"test.user","initialPassword":"abc123","role":"ANNOTATOR"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.loginAccount").value("test.user"));
    }

    @Test
    void disabledUserSessionIsRejected() throws Exception {
        UserDocument stalePrincipalUser = activeUser("admin", Role.ADMIN);
        UserDocument disabledUser = activeUser("admin", Role.ADMIN);
        disabledUser.setEnabled(false);
        when(userRepository.findById("admin")).thenReturn(Optional.of(disabledUser));
        when(authService.currentUser(any()))
                .thenReturn(new CurrentUserResponse("admin", "管理员", "admin", Role.ADMIN));

        mockMvc.perform(get("/auth/me").with(user(UserPrincipal.from(stalePrincipalUser))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(AuthErrorCodes.UNAUTHENTICATED));
    }

    private static UserDocument activeUser(String id, Role role) {
        Instant now = Instant.now();
        UserDocument user = new UserDocument(
                "测试用户", id, id, "$2a$12$hash", role, true, now, now);
        user.setId(id);
        return user;
    }
}
