package com.law.annotation.field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;

class FieldConfigIndexInitializerTests {

    @Test
    void createsUniqueFieldKeyIndexBeforeInitializingDefaults() {
        MongoTemplate mongoTemplate = org.mockito.Mockito.mock(MongoTemplate.class);
        IndexOperations indexOperations = org.mockito.Mockito.mock(IndexOperations.class);
        FieldConfigService service = org.mockito.Mockito.mock(FieldConfigService.class);
        when(mongoTemplate.indexOps(FieldConfigDocument.class)).thenReturn(indexOperations);
        FieldConfigIndexInitializer initializer = new FieldConfigIndexInitializer(mongoTemplate, service);

        initializer.run(new DefaultApplicationArguments());

        ArgumentCaptor<IndexDefinition> captor = ArgumentCaptor.forClass(IndexDefinition.class);
        InOrder order = inOrder(indexOperations, service);
        order.verify(indexOperations).createIndex(captor.capture());
        order.verify(service).initializeDefaults();
        Document keys = captor.getValue().getIndexKeys();
        Document options = captor.getValue().getIndexOptions();
        assertThat(keys.getInteger("fieldKey")).isEqualTo(1);
        assertThat(options.getBoolean("unique")).isTrue();
        assertThat(options.getString("name")).isEqualTo(FieldConfigIndexInitializer.FIELD_KEY_INDEX);
    }
}
