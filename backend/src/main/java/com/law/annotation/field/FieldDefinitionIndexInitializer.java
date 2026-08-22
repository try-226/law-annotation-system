package com.law.annotation.field;

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
public class FieldDefinitionIndexInitializer implements ApplicationRunner {

    public static final String NAME_INDEX = "uk_field_definitions_name";

    private final MongoTemplate mongoTemplate;

    public FieldDefinitionIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        mongoTemplate.indexOps(FieldDefinitionDocument.class).createIndex(
                new Index()
                        .on("name", Sort.Direction.ASC)
                        .unique()
                        .named(NAME_INDEX));
    }
}
