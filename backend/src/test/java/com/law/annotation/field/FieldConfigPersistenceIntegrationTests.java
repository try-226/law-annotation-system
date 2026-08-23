package com.law.annotation.field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.law.annotation.common.enums.Role;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;

class FieldConfigPersistenceIntegrationTests {

    private static MongoServer mongoServer;
    private static MongoClient mongoClient;
    private static MongoTemplate mongoTemplate;
    private static FieldConfigRepository repository;
    private static FieldConfigService service;
    private static FieldConfigIndexInitializer initializer;

    @BeforeAll
    static void startMongo() {
        mongoServer = new MongoServer(new MemoryBackend());
        String connectionString = mongoServer.bindAndGetConnectionString();
        mongoClient = MongoClients.create(connectionString);
        mongoTemplate = new MongoTemplate(mongoClient, "field_config_test");
        MongoRepositoryFactory factory = new MongoRepositoryFactory(mongoTemplate);
        repository = factory.getRepository(FieldConfigRepository.class);
        service = new FieldConfigService(repository);
        initializer = new FieldConfigIndexInitializer(mongoTemplate, service);
    }

    @AfterAll
    static void stopMongo() {
        mongoClient.close();
        mongoServer.shutdown();
    }

    @BeforeEach
    void initializeCollection() {
        mongoTemplate.remove(new Query(), FieldConfigDocument.class);
        initializer.run(new DefaultApplicationArguments());
    }

    @Test
    void initializesExactlyNineFixedDocumentsAndEnforcesUniqueFieldKey() {
        assertThat(repository.findAll()).hasSize(9);
        assertThat(repository.findByFieldKey("lawCategory")).get()
                .extracting(FieldConfigDocument::isRequired)
                .isEqualTo(true);
        assertThat(repository.findByFieldKey("summary")).get()
                .extracting(FieldConfigDocument::isRequired)
                .isEqualTo(false);

        assertThatThrownBy(() -> repository.insert(new FieldConfigDocument(
                        "summary", true, "admin", Instant.now())))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void persistedUpdateChangesNewSnapshotWithoutMutatingOldSnapshot() {
        FieldConfigSnapshot oldSnapshot = service.getCurrentSnapshot();

        service.updateRequired("subjects", true, "admin", Role.ADMIN);
        FieldConfigSnapshot newSnapshot = service.getCurrentSnapshot();

        assertThat(required(oldSnapshot, "subjects")).isFalse();
        assertThat(required(newSnapshot, "subjects")).isTrue();
        assertThat(repository.findByFieldKey("subjects")).get()
                .extracting(FieldConfigDocument::getUpdatedBy)
                .isEqualTo("admin");
    }

    @Test
    void initializationRepairsCoreFieldIfStoredContractWasCorrupted() {
        FieldConfigDocument lawCategory = repository.findByFieldKey("lawCategory").orElseThrow();
        lawCategory.updateRequired(false, "external", Instant.now());
        repository.save(lawCategory);

        service.initializeDefaults();

        assertThat(repository.findByFieldKey("lawCategory")).get()
                .satisfies(document -> {
                    assertThat(document.isRequired()).isTrue();
                    assertThat(document.getUpdatedBy()).isEqualTo(FieldConfigService.SYSTEM_ACTOR);
                });
    }

    private static boolean required(FieldConfigSnapshot snapshot, String fieldKey) {
        return snapshot.article().stream()
                .filter(item -> item.fieldKey().equals(fieldKey))
                .findFirst()
                .orElseThrow()
                .required();
    }
}
