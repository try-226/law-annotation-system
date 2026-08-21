package com.law.annotation.user;

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

class UserIndexInitializerTests {

    @Test
    void createsNamedUniqueNormalizedAccountIndex() {
        MongoTemplate mongoTemplate = org.mockito.Mockito.mock(MongoTemplate.class);
        IndexOperations indexOperations = org.mockito.Mockito.mock(IndexOperations.class);
        when(mongoTemplate.indexOps(UserDocument.class)).thenReturn(indexOperations);
        UserIndexInitializer initializer = new UserIndexInitializer(mongoTemplate);

        initializer.run(new DefaultApplicationArguments());

        ArgumentCaptor<IndexDefinition> captor = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(indexOperations).createIndex(captor.capture());
        Document keys = captor.getValue().getIndexKeys();
        Document options = captor.getValue().getIndexOptions();
        assertThat(keys.getInteger("normalizedAccount")).isEqualTo(1);
        assertThat(options.getBoolean("unique")).isTrue();
        assertThat(options.getString("name")).isEqualTo(UserIndexInitializer.NORMALIZED_ACCOUNT_INDEX);
    }
}
