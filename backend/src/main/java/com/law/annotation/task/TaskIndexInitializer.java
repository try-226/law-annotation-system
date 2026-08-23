package com.law.annotation.task;

import java.util.List;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

@Component
public class TaskIndexInitializer implements ApplicationRunner {

    public static final String ACTIVE_LAW_INDEX = "uk_tasks_active_law";
    public static final String CREATED_AT_INDEX = "idx_tasks_created_at";
    public static final String ANNOTATOR_STATE_INDEX = "idx_tasks_annotator_state";

    private final MongoTemplate mongoTemplate;

    public TaskIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        IndexOperations indexOperations = mongoTemplate.indexOps(TaskDocument.class);
        indexOperations.getIndexInfo().stream()
                .filter(index -> ACTIVE_LAW_INDEX.equals(index.getName()))
                .filter(index -> !matchesRequiredActiveLawIndex(index))
                .findFirst()
                .ifPresent(index -> indexOperations.dropIndex(ACTIVE_LAW_INDEX));
        indexOperations.createIndex(
                new Index()
                        .on("lawId", Sort.Direction.ASC)
                        .unique()
                        .partial(PartialIndexFilter.of(Criteria.where("taskState").in(
                                unfinishedStateNames())))
                        .named(ACTIVE_LAW_INDEX));
        indexOperations.createIndex(
                new Index()
                        .on("createdAt", Sort.Direction.DESC)
                        .named(CREATED_AT_INDEX));
        indexOperations.createIndex(
                new Index()
                        .on("annotatorId", Sort.Direction.ASC)
                        .on("taskState", Sort.Direction.ASC)
                        .named(ANNOTATOR_STATE_INDEX));
    }

    private static boolean matchesRequiredActiveLawIndex(IndexInfo index) {
        if (!index.isUnique() || !index.isIndexForFields(List.of("lawId"))) {
            return false;
        }
        String partialFilterExpression = index.getPartialFilterExpression();
        if (partialFilterExpression == null) {
            return false;
        }
        try {
            return Document.parse(partialFilterExpression).equals(requiredPartialFilter());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static Document requiredPartialFilter() {
        return new Document("taskState", new Document("$in", unfinishedStateNames()));
    }

    private static List<String> unfinishedStateNames() {
        return TaskStateRules.UNFINISHED_STATES.stream()
                .map(Enum::name)
                .toList();
    }
}
