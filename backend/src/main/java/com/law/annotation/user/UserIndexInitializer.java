package com.law.annotation.user;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserIndexInitializer implements ApplicationRunner {

    public static final String NORMALIZED_ACCOUNT_INDEX = "uk_users_normalized_account";

    private final MongoTemplate mongoTemplate;

    public UserIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        mongoTemplate.indexOps(UserDocument.class).createIndex(
                new Index()
                        .on("normalizedAccount", Sort.Direction.ASC)
                        .unique()
                        .named(NORMALIZED_ACCOUNT_INDEX));
    }
}
