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
public class FieldConfigIndexInitializer implements ApplicationRunner {

    public static final String FIELD_KEY_INDEX = "uk_field_config_field_key";

    private final MongoTemplate mongoTemplate;
    private final FieldConfigService fieldConfigService;

    public FieldConfigIndexInitializer(
            MongoTemplate mongoTemplate,
            FieldConfigService fieldConfigService) {
        this.mongoTemplate = mongoTemplate;
        this.fieldConfigService = fieldConfigService;
    }

    @Override
    public void run(ApplicationArguments args) {
        mongoTemplate.indexOps(FieldConfigDocument.class).createIndex(
                new Index()
                        .on("fieldKey", Sort.Direction.ASC)
                        .unique()
                        .named(FIELD_KEY_INDEX));
        fieldConfigService.initializeDefaults();
    }
}
