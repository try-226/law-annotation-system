package com.law.annotation.auth;

import com.law.annotation.auth.dto.CurrentUserResponse;
import com.law.annotation.auth.dto.LoginRequest;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.user.UserDocument;
import com.law.annotation.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final UserService userService;

    public AuthService(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            UserService userService) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.userService = userService;
    }

    public CurrentUserResponse login(
            LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.loginAccount(), request.password()));
            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            if (principal.role() != request.expectedRole()) {
                throw invalidCredentials();
            }

            sessionAuthenticationStrategy.onAuthentication(
                    authentication, servletRequest, servletResponse);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, servletRequest, servletResponse);
            return CurrentUserResponse.from(userService.requireDocument(principal.id()));
        } catch (AuthenticationException exception) {
            throw invalidCredentials();
        }
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }

    public CurrentUserResponse currentUser(UserPrincipal principal) {
        return CurrentUserResponse.from(userService.requireDocument(principal.id()));
    }

    public CurrentUserResponse updateProfile(UserPrincipal principal, String name) {
        userService.updateName(principal.id(), name);
        UserDocument user = userService.requireDocument(principal.id());
        return CurrentUserResponse.from(user);
    }

    public void changePassword(
            UserPrincipal principal,
            String oldPassword,
            String newPassword,
            String confirmPassword) {
        userService.changePassword(principal.id(), oldPassword, newPassword, confirmPassword);
    }

    private static ApiException invalidCredentials() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                AuthErrorCodes.INVALID_CREDENTIALS,
                "账号或密码错误");
    }
}
