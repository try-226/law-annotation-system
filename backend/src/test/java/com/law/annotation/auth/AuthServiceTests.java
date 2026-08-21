package com.law.annotation.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.law.annotation.auth.dto.LoginRequest;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.user.UserDocument;
import com.law.annotation.user.UserService;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;

class AuthServiceTests {

    private final AuthenticationManager authenticationManager =
            org.mockito.Mockito.mock(AuthenticationManager.class);
    private final SecurityContextRepository securityContextRepository =
            org.mockito.Mockito.mock(SecurityContextRepository.class);
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy =
            org.mockito.Mockito.mock(SessionAuthenticationStrategy.class);
    private final UserService userService = org.mockito.Mockito.mock(UserService.class);
    private final AuthService authService = new AuthService(
            authenticationManager,
            securityContextRepository,
            sessionAuthenticationStrategy,
            userService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void successfulLoginPersistsAuthenticatedSession() {
        UserDocument user = user("u1", Role.ADMIN, true);
        UserPrincipal principal = UserPrincipal.from(user);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userService.requireDocument("u1")).thenReturn(user);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = authService.login(
                new LoginRequest("admin", "admin123", Role.ADMIN), request, response);

        assertThat(result.id()).isEqualTo("u1");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(authentication);
        verify(sessionAuthenticationStrategy).onAuthentication(authentication, request, response);
        verify(securityContextRepository).saveContext(any(), any(), any());
    }

    @Test
    void wrongPasswordUsesUniformPublicError() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("internal reason"));

        assertInvalidCredentials(() -> authService.login(
                new LoginRequest("admin", "wrong", Role.ADMIN),
                new MockHttpServletRequest(),
                new MockHttpServletResponse()));
    }

    @Test
    void wrongExpectedRoleUsesUniformPublicError() {
        UserDocument user = user("u1", Role.ANNOTATOR, true);
        UserPrincipal principal = UserPrincipal.from(user);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(authentication);

        assertInvalidCredentials(() -> authService.login(
                new LoginRequest("annotator", "valid123", Role.ADMIN),
                new MockHttpServletRequest(),
                new MockHttpServletResponse()));
    }

    @Test
    void logoutInvalidatesSessionAndClearsContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = (MockHttpSession) request.getSession();
        UserDocument user = user("u1", Role.ADMIN, true);
        UserPrincipal principal = UserPrincipal.from(user);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        authService.logout(request, new MockHttpServletResponse());

        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private void assertInvalidCredentials(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.getCode()).isEqualTo(AuthErrorCodes.INVALID_CREDENTIALS);
                    assertThat(apiException.getUserMessage()).isEqualTo("账号或密码错误");
                });
    }

    private static UserDocument user(String id, Role role, boolean enabled) {
        Instant now = Instant.now();
        UserDocument user = new UserDocument(
                "测试用户", "admin", "admin", "$2a$12$hash", role, enabled, now, now);
        user.setId(id);
        return user;
    }
}
