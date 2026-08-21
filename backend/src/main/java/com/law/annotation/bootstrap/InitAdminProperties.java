package com.law.annotation.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.init-admin")
public record InitAdminProperties(
        boolean enabled,
        String username,
        String password,
        String name) {
}
