package com.law.annotation.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;

class TaskSubmissionIndexInitializerTests {

    @Test
    void createsUniqueInitialSubmissionIndex() throws Exception {
        MongoTemplate mongoTemplate = org.mockito.Mockito.mock(MongoTemplate.class);
        IndexOperations indexOperations = org.mockito.Mockito.mock(IndexOperations.class);
        when(mongoTemplate.indexOps(TaskSubmissionDocument.class)).thenReturn(indexOperations);
        ArgumentCaptor<IndexDefinition> captor = ArgumentCaptor.forClass(IndexDefinition.class);

        new TaskSubmissionIndexInitializer(mongoTemplate)
                .run(new DefaultApplicationArguments());

        verify(indexOperations).createIndex(captor.capture());
        IndexDefinition index = captor.getValue();
        assertThat(index.getIndexKeys()).isEqualTo(new Document("taskId", 1)
                .append("submissionNo", 1));
        assertThat(index.getIndexOptions().getBoolean("unique")).isTrue();
        assertThat(index.getIndexOptions().getString("name"))
                .isEqualTo(TaskSubmissionIndexInitializer.UNIQUE_TASK_SUBMISSION_INDEX);
    }
}
