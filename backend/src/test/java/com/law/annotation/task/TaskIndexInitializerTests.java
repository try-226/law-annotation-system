package com.law.annotation.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;

class TaskIndexInitializerTests {

    @Test
    void createsPartialUniqueActiveLawIndex() throws Exception {
        MongoTemplate mongoTemplate = org.mockito.Mockito.mock(MongoTemplate.class);
        IndexOperations indexOperations = org.mockito.Mockito.mock(IndexOperations.class);
        when(mongoTemplate.indexOps(TaskDocument.class)).thenReturn(indexOperations);
        when(indexOperations.getIndexInfo()).thenReturn(List.of());
        ArgumentCaptor<IndexDefinition> captor = ArgumentCaptor.forClass(IndexDefinition.class);

        new TaskIndexInitializer(mongoTemplate).run(new DefaultApplicationArguments());

        org.mockito.Mockito.verify(indexOperations, org.mockito.Mockito.times(3))
                .createIndex(captor.capture());
        List<IndexDefinition> indexes = captor.getAllValues();
        IndexDefinition activeLaw = indexes.stream()
                .filter(index -> TaskIndexInitializer.ACTIVE_LAW_INDEX.equals(
                        index.getIndexOptions().getString("name")))
                .findFirst()
                .orElseThrow();
        assertThat(activeLaw.getIndexKeys()).isEqualTo(new Document("lawId", 1));
        assertThat(activeLaw.getIndexOptions().getBoolean("unique")).isTrue();
        assertThat(activeLaw.getIndexOptions().get("partialFilterExpression", Document.class))
                .isEqualTo(new Document(
                        "taskState",
                        new Document("$in", List.of(
                                "PENDING_ANNOTATION",
                                "ANNOTATING",
                                "PENDING_REVIEW",
                                "PARTIALLY_REJECTED",
                                "PENDING_REREVIEW"))));
    }

    @Test
    void replacesLegacyActiveBooleanIndexDefinition() throws Exception {
        MongoTemplate mongoTemplate = org.mockito.Mockito.mock(MongoTemplate.class);
        IndexOperations indexOperations = org.mockito.Mockito.mock(IndexOperations.class);
        IndexInfo legacy = org.mockito.Mockito.mock(IndexInfo.class);
        when(mongoTemplate.indexOps(TaskDocument.class)).thenReturn(indexOperations);
        when(indexOperations.getIndexInfo()).thenReturn(List.of(legacy));
        when(legacy.getName()).thenReturn(TaskIndexInitializer.ACTIVE_LAW_INDEX);
        when(legacy.isUnique()).thenReturn(true);
        when(legacy.isIndexForFields(List.of("lawId"))).thenReturn(true);
        when(legacy.getPartialFilterExpression()).thenReturn("{\"active\":true}");

        new TaskIndexInitializer(mongoTemplate).run(new DefaultApplicationArguments());

        org.mockito.Mockito.verify(indexOperations)
                .dropIndex(TaskIndexInitializer.ACTIVE_LAW_INDEX);
    }
}
