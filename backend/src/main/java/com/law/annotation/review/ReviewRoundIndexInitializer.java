package com.law.annotation.review;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Component
public class ReviewRoundIndexInitializer implements ApplicationRunner {

    public static final String UNIQUE_TASK_SOURCE_INDEX = "uk_review_rounds_task_source";
    public static final String UNIQUE_TASK_ROUND_INDEX = "uk_review_rounds_task_round_no";
    public static final String REVIEWER_COMPLETION_INDEX = "idx_review_rounds_reviewer_completion";

    private final MongoTemplate mongoTemplate;

    public ReviewRoundIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        mongoTemplate.indexOps(ReviewRoundDocument.class).createIndex(
                new Index()
                        .on("taskId", Sort.Direction.ASC)
                        .on("sourceSubmissionId", Sort.Direction.ASC)
                        .unique()
                        .named(UNIQUE_TASK_SOURCE_INDEX));
        mongoTemplate.indexOps(ReviewRoundDocument.class).createIndex(
                new Index()
                        .on("taskId", Sort.Direction.ASC)
                        .on("roundNo", Sort.Direction.ASC)
                        .unique()
                        .named(UNIQUE_TASK_ROUND_INDEX));
        mongoTemplate.indexOps(ReviewRoundDocument.class).createIndex(
                new Index()
                        .on("reviewerId", Sort.Direction.ASC)
                        .on("completedAt", Sort.Direction.ASC)
                        .named(REVIEWER_COMPLETION_INDEX));
    }
}
