package com.law.annotation.field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.law.annotation.common.exception.ApiException;
import com.law.annotation.field.dto.CreateFieldDefinitionRequest;
import com.law.annotation.field.dto.FieldDefinitionResponse;
import com.law.annotation.field.dto.UpdateFieldDefinitionRequest;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;

class FieldDefinitionPersistenceIntegrationTest {

    private static MongoServer mongoServer;
    private static MongoClient mongoClient;
    private static MongoTemplate mongoTemplate;
    private static FieldDefinitionRepository repository;
    private static FieldDefinitionService service;

    @BeforeAll
    static void startMongo() {
        mongoServer = new MongoServer(new MemoryBackend());
        String connectionString = mongoServer.bindAndGetConnectionString();
        mongoClient = MongoClients.create(connectionString);
        mongoTemplate = new MongoTemplate(mongoClient, "field_definition_test");
        repository = new MongoRepositoryFactory(mongoTemplate)
                .getRepository(FieldDefinitionRepository.class);
        service = new FieldDefinitionService(repository);
        new FieldDefinitionIndexInitializer(mongoTemplate).run(new DefaultApplicationArguments());
    }

    @AfterAll
    static void stopMongo() {
        mongoClient.close();
        mongoServer.shutdown();
    }

    @BeforeEach
    void clearDocuments() {
        mongoTemplate.remove(new Query(), FieldDefinitionDocument.class);
    }

    @Test
    void uniqueNameIndexExistsAndRejectsDirectDuplicateInsert() {
        IndexInfo index = mongoTemplate.indexOps(FieldDefinitionDocument.class)
                .getIndexInfo().stream()
                .filter(candidate -> FieldDefinitionIndexInitializer.NAME_INDEX.equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        assertThat(index.isUnique()).isTrue();
        assertThat(index.getIndexFields()).singleElement()
                .satisfies(field -> assertThat(field.getKey()).isEqualTo("name"));

        repository.insert(document("field-1", "penalty_amount"));
        assertThatThrownBy(() -> repository.insert(document("field-2", "penalty_amount")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void serviceCreatesListsUpdatesAndSoftDeactivates() {
        FieldDefinitionResponse created = service.create(new CreateFieldDefinitionRequest(
                " penalty_amount ",
                " 处罚金额 ",
                " 金额说明 ",
                FieldType.NUMBER,
                true,
                List.of()));
        assertThat(created.name()).isEqualTo("penalty_amount");
        assertThat(created.status()).isEqualTo(FieldDefinitionStatus.ACTIVE);
        assertThat(service.list(0, 20).items()).extracting(FieldDefinitionResponse::id)
                .containsExactly(created.id());

        FieldDefinitionResponse updated = service.update(created.id(), new UpdateFieldDefinitionRequest(
                null,
                null,
                "处罚数额",
                "更新说明",
                false,
                List.of(),
                FieldDefinitionStatus.ACTIVE));
        assertThat(updated.name()).isEqualTo("penalty_amount");
        assertThat(updated.displayName()).isEqualTo("处罚数额");

        FieldDefinitionResponse inactive = service.deactivate(created.id());
        assertThat(inactive.status()).isEqualTo(FieldDefinitionStatus.INACTIVE);
        assertThat(repository.findById(created.id())).get()
                .extracting(FieldDefinitionDocument::getStatus)
                .isEqualTo(FieldDefinitionStatus.INACTIVE);
    }

    @Test
    void inactiveDefinitionStillReservesName() {
        FieldDefinitionResponse created = service.create(new CreateFieldDefinitionRequest(
                "penalty_amount", "处罚金额", null, FieldType.NUMBER, false, List.of()));
        service.deactivate(created.id());

        assertThatThrownBy(() -> service.create(new CreateFieldDefinitionRequest(
                        "penalty_amount", "另一金额", null, FieldType.NUMBER, false, List.of())))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(FieldDefinitionErrorCodes.NAME_ALREADY_EXISTS);
    }

    private static FieldDefinitionDocument document(String id, String name) {
        Instant now = Instant.parse("2026-08-22T00:00:00Z");
        FieldDefinitionDocument document = new FieldDefinitionDocument(
                name,
                "处罚金额",
                null,
                FieldType.NUMBER,
                false,
                List.of(),
                FieldDefinitionStatus.ACTIVE,
                now,
                now);
        document.setId(id);
        return document;
    }
}
