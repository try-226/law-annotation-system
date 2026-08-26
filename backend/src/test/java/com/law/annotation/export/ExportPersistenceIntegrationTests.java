package com.law.annotation.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.export.dto.LawExportRequest;
import com.law.annotation.export.formatter.PlainExportCsvFormatter;
import com.law.annotation.export.formatter.PlainExportJsonFormatter;
import com.law.annotation.law.ArticleSnapshot;
import com.law.annotation.law.LawDocument;
import com.law.annotation.law.LawDomainRules;
import com.law.annotation.law.LawErrorCodes;
import com.law.annotation.law.LawRepository;
import com.law.annotation.law.LawStructureNode;
import com.law.annotation.law.LawStructureNodeType;
import com.law.annotation.law.PendingChangeSet;
import com.law.annotation.version.ContentVersionDocument;
import com.law.annotation.version.ContentVersionRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;

class ExportPersistenceIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-08-26T00:00:00Z");

    private static MongoServer mongoServer;
    private static MongoClient mongoClient;
    private static MongoTemplate mongoTemplate;
    private static LawRepository lawRepository;
    private static ContentVersionRepository contentVersionRepository;
    private static ObjectMapper objectMapper;
    private static ExportService service;

    @BeforeAll
    static void startMongo() {
        mongoServer = new MongoServer(new MemoryBackend());
        mongoClient = MongoClients.create(mongoServer.bindAndGetConnectionString());
        mongoTemplate = new MongoTemplate(mongoClient, "law_pr15_test");
        MongoRepositoryFactory factory = new MongoRepositoryFactory(mongoTemplate);
        lawRepository = factory.getRepository(LawRepository.class);
        contentVersionRepository = factory.getRepository(ContentVersionRepository.class);
        objectMapper = JsonMapper.builder().findAndAddModules().build();
        service = new ExportService(
                lawRepository,
                contentVersionRepository,
                new PlainExportCsvFormatter(),
                new PlainExportJsonFormatter(objectMapper));
    }

    @AfterAll
    static void stopMongo() {
        mongoClient.close();
        mongoServer.shutdown();
    }

    @BeforeEach
    void clearDocuments() {
        mongoTemplate.remove(new Query(), LawDocument.class);
        mongoTemplate.remove(new Query(), ContentVersionDocument.class);
    }

    @Test
    void persistedPendingRevisionLawExportsLatestCWithCurrentBaseAndStructure() throws Exception {
        ContentVersionDocument c1 = version("content-1", 1, "C1旧正文");
        ContentVersionDocument c2 = version("content-2", 2, "C2最新正文");
        contentVersionRepository.insert(c1);
        contentVersionRepository.insert(c2);
        LawDocument law = law("content-2", true);
        lawRepository.save(law);

        ExportedFile file = service.export("law-1", new LawExportRequest(
                LawExportRequest.Scope.WHOLE,
                List.of(),
                LawExportRequest.Type.PLAIN,
                LawExportRequest.Format.JSON));

        JsonNode json = objectMapper.readTree(file.content());
        assertThat(json.path("law").path("name").asText()).isEqualTo("当前法律名称");
        assertThat(json.path("law").path("currentContentVersionId").asText())
                .isEqualTo("content-2");
        assertThat(json.path("structure").get(0).path("title").asText())
                .isEqualTo("当前第一章");
        assertThat(json.path("articles").get(0).path("body").asText())
                .isEqualTo("C2最新正文");
        assertThat(mongoTemplate.getCollection("annotation_versions").countDocuments()).isZero();
    }

    @Test
    void persistedSoftDeletedLawCannotUseTheNormalExportEndpoint() {
        contentVersionRepository.insert(version("content-1", 1, "正文"));
        LawDocument deleted = law("content-1", false);
        deleted.markDeleted(NOW.plusSeconds(60));
        lawRepository.save(deleted);

        assertThatThrownBy(() -> service.export("law-1", new LawExportRequest(
                        LawExportRequest.Scope.WHOLE,
                        List.of(),
                        LawExportRequest.Type.PLAIN,
                        LawExportRequest.Format.JSON)))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(LawErrorCodes.NOT_FOUND);
    }

    private static ContentVersionDocument version(String id, int seq, String body) {
        return new ContentVersionDocument(
                id,
                "law-1",
                seq,
                List.of(new ArticleSnapshot("article-1", "第一条", body, 0)),
                "admin-1",
                NOW);
    }

    private static LawDocument law(String contentVersionId, boolean pendingRevision) {
        return new LawDocument(
                "law-1",
                "当前法律名称",
                LawDomainRules.normalizeLawName("当前法律名称"),
                "当前制定机关",
                LocalDate.of(2026, 8, 19),
                ValidityStatus.ACTIVE,
                List.of(new LawStructureNode(
                        "chapter-1",
                        LawStructureNodeType.CHAPTER,
                        "当前第一章",
                        null,
                        0,
                        List.of("article-1"))),
                null,
                contentVersionId,
                pendingRevision ? "annotation-1" : null,
                pendingRevision,
                pendingRevision
                        ? PendingChangeSet.empty().recordModification("article-1")
                        : PendingChangeSet.empty(),
                NOW,
                NOW);
    }
}
