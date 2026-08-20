package com.law.annotation.auth;

import com.law.annotation.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ActiveUserFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final RestSecurityErrorHandler errorHandler;

    public ActiveUserFilter(UserRepository userRepository, RestSecurityErrorHandler errorHandler) {
        this.userRepository = userRepository;
        this.errorHandler = errorHandler;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            boolean active = userRepository.findById(principal.id())
                    .map(user -> user.isEnabled() && user.getRole() == principal.role())
                    .orElse(false);
            if (!active) {
                SecurityContextHolder.clearContext();
                if (request.getSession(false) != null) {
                    request.getSession(false).invalidate();
                }
                errorHandler.commence(
                        request,
                        response,
                        new InsufficientAuthenticationException("User session is no longer active"));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
