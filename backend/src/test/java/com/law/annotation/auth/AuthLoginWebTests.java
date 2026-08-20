package com.law.annotation.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.law.annotation.common.enums.Role;
import com.law.annotation.common.exception.GlobalExceptionHandler;
import com.law.annotation.user.UserDocument;
import com.law.annotation.user.UserRepository;
import com.law.annotation.user.UserService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@Import({
        SecurityConfig.class,
        RestSecurityErrorHandler.class,
        ActiveUserFilter.class,
        AuthService.class,
        GlobalExceptionHandler.class
})
class AuthLoginWebTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private MongoUserDetailsService mongoUserDetailsService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void validCredentialsAndExpectedRoleCreateSession() throws Exception {
        UserDocument admin = user("admin-id", Role.ADMIN, true, "admin123");
        when(mongoUserDetailsService.loadUserByUsername("admin"))
                .thenReturn(UserPrincipal.from(admin));
        when(userService.requireDocument("admin-id")).thenReturn(admin);

        var result = mockMvc.perform(post("/auth/login")
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginAccount":"admin","password":"admin123","expectedRole":"ADMIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNotNull();
    }

    @Test
    void wrongPasswordAndWrongRoleHaveSamePublicError() throws Exception {
        UserDocument adminForPassword = user("admin-id", Role.ADMIN, true, "admin123");
        when(mongoUserDetailsService.loadUserByUsername("admin"))
                .thenReturn(UserPrincipal.from(adminForPassword));
        assertInvalidLogin("admin", "wrong123", "ADMIN");

        UserDocument annotatorForRole = user("annotator-id", Role.ANNOTATOR, true, "annotator123");
        when(mongoUserDetailsService.loadUserByUsername("annotator"))
                .thenReturn(UserPrincipal.from(annotatorForRole));
        assertInvalidLogin("annotator", "annotator123", "ADMIN");
    }

    @Test
    void disabledUserHasSamePublicLoginError() throws Exception {
        UserDocument disabled = user("disabled-id", Role.ANNOTATOR, false, "annotator123");
        when(mongoUserDetailsService.loadUserByUsername("disabled"))
                .thenReturn(UserPrincipal.from(disabled));

        assertInvalidLogin("disabled", "annotator123", "ANNOTATOR");
    }

    @Test
    void unknownAccountHasSamePublicLoginError() throws Exception {
        when(mongoUserDetailsService.loadUserByUsername("missing"))
                .thenThrow(new UsernameNotFoundException("internal detail"));

        assertInvalidLogin("missing", "any123", "ANNOTATOR");
    }

    private void assertInvalidLogin(String account, String password, String role) throws Exception {
        mockMvc.perform(post("/auth/login")
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginAccount":"%s","password":"%s","expectedRole":"%s"}
                                """.formatted(account, password, role)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(AuthErrorCodes.INVALID_CREDENTIALS))
                .andExpect(jsonPath("$.error.userMessage").value("账号或密码错误"));
    }

    private UserDocument user(String id, Role role, boolean enabled, String password) {
        Instant now = Instant.now();
        UserDocument user = new UserDocument(
                "测试用户",
                id,
                id,
                passwordEncoder.encode(password),
                role,
                enabled,
                now,
                now);
        user.setId(id);
        return user;
    }
}
