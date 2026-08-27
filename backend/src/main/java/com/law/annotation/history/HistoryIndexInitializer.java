package com.law.annotation.history;

import com.law.annotation.task.TaskDocument;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Component
public class HistoryIndexInitializer implements ApplicationRunner {

    public static final String TASK_LAW_CREATED_AT_INDEX = "ix_tasks_law_created_at";

    private final MongoTemplate mongoTemplate;

    public HistoryIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        mongoTemplate.indexOps(TaskDocument.class).createIndex(
                new Index()
                        .on("lawId", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.ASC)
                        .on("_id", Sort.Direction.ASC)
                        .named(TASK_LAW_CREATED_AT_INDEX));
    }
}
