package com.law.annotation.bootstrap;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.law.annotation.common.enums.Role;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.HttpStatus;

class BootstrapAdminRunnerTests {

    private final UserService userService = org.mockito.Mockito.mock(UserService.class);

    @Test
    void createsAdminOnlyWhenEnabledAndNoAdminExists() throws Exception {
        InitAdminProperties properties = new InitAdminProperties(
                true, "admin", "admin123", "系统管理员");
        BootstrapAdminRunner runner = new BootstrapAdminRunner(userService, properties);
        when(userService.countAdmins()).thenReturn(0L);

        runner.run(new DefaultApplicationArguments());

        verify(userService).createUser("系统管理员", "admin", "admin123", Role.ADMIN);
    }

    @Test
    void existingAdminMakesRestartIdempotent() throws Exception {
        InitAdminProperties properties = new InitAdminProperties(
                true, "different", "different123", "其他管理员");
        BootstrapAdminRunner runner = new BootstrapAdminRunner(userService, properties);
        when(userService.countAdmins()).thenReturn(1L);

        runner.run(new DefaultApplicationArguments());

        verify(userService, never()).createUser(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void disabledBootstrapDoesNotCreateAdmin() throws Exception {
        InitAdminProperties properties = new InitAdminProperties(false, "", "", "");
        BootstrapAdminRunner runner = new BootstrapAdminRunner(userService, properties);
        when(userService.countAdmins()).thenReturn(0L);

        runner.run(new DefaultApplicationArguments());

        verify(userService, never()).createUser(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void invalidBootstrapConfigurationFailsWithoutLeakingPassword() {
        InitAdminProperties properties = new InitAdminProperties(
                true, "admin", "secret-password", "系统管理员");
        BootstrapAdminRunner runner = new BootstrapAdminRunner(userService, properties);
        when(userService.countAdmins()).thenReturn(0L);
        when(userService.createUser("系统管理员", "admin", "secret-password", Role.ADMIN))
                .thenThrow(new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "COMMON.VALIDATION_FAILED",
                        "请求参数校验失败"));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("首次管理员初始化失败")
                .hasMessageNotContaining("secret-password");
    }
}
