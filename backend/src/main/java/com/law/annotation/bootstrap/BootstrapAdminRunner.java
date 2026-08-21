package com.law.annotation.bootstrap;

import com.law.annotation.common.enums.Role;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@EnableConfigurationProperties(InitAdminProperties.class)
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final UserService userService;
    private final InitAdminProperties properties;

    public BootstrapAdminRunner(UserService userService, InitAdminProperties properties) {
        this.userService = userService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userService.countAdmins() > 0) {
            LOGGER.info("Bootstrap administrator initialization skipped because an administrator exists");
            return;
        }
        if (!properties.enabled()) {
            LOGGER.info("Bootstrap administrator initialization is disabled");
            return;
        }

        try {
            userService.createUser(
                    properties.name(),
                    properties.username(),
                    properties.password(),
                    Role.ADMIN);
            LOGGER.info("Bootstrap administrator created successfully");
        } catch (ApiException exception) {
            throw new IllegalStateException(
                    "首次管理员初始化失败：" + exception.getUserMessage(),
                    exception);
        }
    }
}
