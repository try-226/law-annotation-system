package com.law.annotation.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.law.annotation.version.ContentVersionDocument;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;

class LawDomainIndexInitializerTests {

    @Test
    void createsNamedIndexesMatchingDomainUniqueness() {
        MongoTemplate mongoTemplate = org.mockito.Mockito.mock(MongoTemplate.class);
        IndexOperations lawIndexes = org.mockito.Mockito.mock(IndexOperations.class);
        IndexOperations versionIndexes = org.mockito.Mockito.mock(IndexOperations.class);
        IndexOperations auditIndexes = org.mockito.Mockito.mock(IndexOperations.class);
        when(mongoTemplate.indexOps(LawDocument.class)).thenReturn(lawIndexes);
        when(mongoTemplate.indexOps(ContentVersionDocument.class)).thenReturn(versionIndexes);
        when(mongoTemplate.indexOps(LawAuditDocument.class)).thenReturn(auditIndexes);
        LawDomainIndexInitializer initializer = new LawDomainIndexInitializer(mongoTemplate);

        initializer.run(new DefaultApplicationArguments());

        IndexDefinition lawIndex = capture(lawIndexes);
        assertIndex(
                lawIndex,
                new Document("normalizedName", 1),
                LawDomainIndexInitializer.NORMALIZED_NAME_INDEX,
                true);

        IndexDefinition versionIndex = capture(versionIndexes);
        assertIndex(
                versionIndex,
                new Document("lawId", 1).append("seq", 1),
                LawDomainIndexInitializer.CONTENT_VERSION_SEQUENCE_INDEX,
                true);

        IndexDefinition auditIndex = capture(auditIndexes);
        assertIndex(
                auditIndex,
                new Document("lawId", 1).append("operatedAt", -1),
                LawDomainIndexInitializer.AUDIT_HISTORY_INDEX,
                false);
    }

    private static IndexDefinition capture(IndexOperations indexOperations) {
        ArgumentCaptor<IndexDefinition> captor = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(indexOperations).createIndex(captor.capture());
        return captor.getValue();
    }

    private static void assertIndex(
            IndexDefinition index,
            Document expectedKeys,
            String expectedName,
            boolean unique) {
        assertThat(index.getIndexKeys()).isEqualTo(expectedKeys);
        assertThat(index.getIndexOptions().getString("name")).isEqualTo(expectedName);
        assertThat(index.getIndexOptions().getBoolean("unique", false)).isEqualTo(unique);
    }
}
