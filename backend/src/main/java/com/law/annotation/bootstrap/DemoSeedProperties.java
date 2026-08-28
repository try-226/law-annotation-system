package com.law.annotation.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.demo-data")
public record DemoSeedProperties(
        boolean enabled,
        String annotatorUsername,
        String annotatorPassword) {
}
