package com.law.annotation.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.law.annotation.task.TaskDocument;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;

class HistoryIndexInitializerTests {

    @Test
    void createsTaskLawHistoryIndexWithStableKeyOrder() throws Exception {
        MongoTemplate mongoTemplate = org.mockito.Mockito.mock(MongoTemplate.class);
        IndexOperations operations = org.mockito.Mockito.mock(IndexOperations.class);
        when(mongoTemplate.indexOps(TaskDocument.class)).thenReturn(operations);
        ArgumentCaptor<IndexDefinition> index = ArgumentCaptor.forClass(IndexDefinition.class);

        new HistoryIndexInitializer(mongoTemplate).run(new DefaultApplicationArguments());

        verify(operations).createIndex(index.capture());
        assertThat(index.getValue().getIndexKeys())
                .isEqualTo(new Document("lawId", 1).append("createdAt", 1).append("_id", 1));
        assertThat(index.getValue().getIndexOptions().getString("name"))
                .isEqualTo(HistoryIndexInitializer.TASK_LAW_CREATED_AT_INDEX);
    }
}
