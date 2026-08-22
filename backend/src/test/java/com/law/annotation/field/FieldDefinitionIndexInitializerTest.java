package com.law.annotation.field;

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

class FieldDefinitionIndexInitializerTest {

    @Test
    void createsNamedUniqueNameIndex() {
        MongoTemplate mongoTemplate = org.mockito.Mockito.mock(MongoTemplate.class);
        IndexOperations indexOperations = org.mockito.Mockito.mock(IndexOperations.class);
        when(mongoTemplate.indexOps(FieldDefinitionDocument.class)).thenReturn(indexOperations);
        FieldDefinitionIndexInitializer initializer = new FieldDefinitionIndexInitializer(mongoTemplate);

        initializer.run(new DefaultApplicationArguments());

        ArgumentCaptor<IndexDefinition> captor = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(indexOperations).createIndex(captor.capture());
        Document keys = captor.getValue().getIndexKeys();
        Document options = captor.getValue().getIndexOptions();
        assertThat(keys.getInteger("name")).isEqualTo(1);
        assertThat(options.getBoolean("unique")).isTrue();
        assertThat(options.getString("name")).isEqualTo(FieldDefinitionIndexInitializer.NAME_INDEX);
    }
}
