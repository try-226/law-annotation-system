package com.law.annotation.annotation;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Component
public class TaskSubmissionIndexInitializer implements ApplicationRunner {

    public static final String UNIQUE_TASK_SUBMISSION_INDEX =
            "uk_task_submissions_task_no";

    private final MongoTemplate mongoTemplate;

    public TaskSubmissionIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        mongoTemplate.indexOps(TaskSubmissionDocument.class).createIndex(
                new Index()
                        .on("taskId", Sort.Direction.ASC)
                        .on("submissionNo", Sort.Direction.ASC)
                        .unique()
                        .named(UNIQUE_TASK_SUBMISSION_INDEX));
    }
}
