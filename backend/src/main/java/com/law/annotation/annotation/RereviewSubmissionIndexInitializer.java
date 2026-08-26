package com.law.annotation.annotation;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

@Component
public class RereviewSubmissionIndexInitializer implements ApplicationRunner {

    public static final String UNIQUE_SOURCE_REVIEW_ROUND_INDEX =
            "uk_task_submissions_source_review_round";

    private final MongoTemplate mongoTemplate;

    public RereviewSubmissionIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        mongoTemplate.indexOps(TaskSubmissionDocument.class).createIndex(
                new Index()
                        .on("sourceReviewRoundId", Sort.Direction.ASC)
                        .unique()
                        .partial(PartialIndexFilter.of(
                                Criteria.where("sourceReviewRoundId").type(2)))
                        .named(UNIQUE_SOURCE_REVIEW_ROUND_INDEX));
    }
}
