package com.law.annotation.user;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class UserBusinessUsageConfiguration {

    @Bean
    @ConditionalOnMissingBean(UserBusinessUsagePort.class)
    UserBusinessUsagePort noBusinessUsagePort() {
        return new UserBusinessUsagePort() {
            @Override
            public boolean hasActiveTask(String userId) {
                return false;
            }

            @Override
            public boolean hasUnfinishedReviewRound(String userId) {
                return false;
            }

            @Override
            public boolean hasBusinessHistory(String userId) {
                return false;
            }
        };
    }
}
