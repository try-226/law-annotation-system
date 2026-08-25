package com.law.annotation.version;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Component
public class AnnotationVersionIndexInitializer implements ApplicationRunner {

    public static final String UNIQUE_LAW_SEQUENCE_INDEX = "uk_annotation_versions_law_seq";
    public static final String UNIQUE_SOURCE_TASK_INDEX = "uk_annotation_versions_source_task";

    private final MongoTemplate mongoTemplate;

    public AnnotationVersionIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        mongoTemplate.indexOps(AnnotationVersionDocument.class).createIndex(
                new Index()
                        .on("lawId", Sort.Direction.ASC)
                        .on("seq", Sort.Direction.ASC)
                        .unique()
                        .named(UNIQUE_LAW_SEQUENCE_INDEX));
        mongoTemplate.indexOps(AnnotationVersionDocument.class).createIndex(
                new Index()
                        .on("sourceTaskId", Sort.Direction.ASC)
                        .unique()
                        .named(UNIQUE_SOURCE_TASK_INDEX));
    }
}
