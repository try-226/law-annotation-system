package com.law.annotation.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.version.ContentVersionDocument;
import com.law.annotation.version.ContentVersionRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;

class LawPersistenceIntegrationTests {

    private static MongoServer mongoServer;
    private static MongoClient mongoClient;
    private static MongoTemplate mongoTemplate;
    private static LawRepository lawRepository;
    private static ContentVersionRepository contentVersionRepository;
    private static LawAuditRepository lawAuditRepository;

    @BeforeAll
    static void startMongo() {
        mongoServer = new MongoServer(new MemoryBackend());
        String connectionString = mongoServer.bindAndGetConnectionString();
        mongoClient = MongoClients.create(connectionString);
        mongoTemplate = new MongoTemplate(mongoClient, "law_domain_test");
        MongoRepositoryFactory factory = new MongoRepositoryFactory(mongoTemplate);
        lawRepository = factory.getRepository(LawRepository.class);
        contentVersionRepository = factory.getRepository(ContentVersionRepository.class);
        lawAuditRepository = factory.getRepository(LawAuditRepository.class);
        new LawDomainIndexInitializer(mongoTemplate).run(new DefaultApplicationArguments());
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
        mongoTemplate.remove(new Query(), LawAuditDocument.class);
    }

    @Test
    void createsInitialLawAndPointsToItsC1() {
        LawCreationService service = new LawCreationService(
                lawRepository, contentVersionRepository, mongoTemplate);
        NewArticleDraft article = new NewArticleDraft("第一条", "第一条正文", 0);
        LawStructureNode chapter = new LawStructureNode(
                "chapter-1",
                LawStructureNodeType.CHAPTER,
                "第一章 总则",
                null,
                0,
                List.of(article.articleId()));

        InitialLawCreation creation = service.createInitialLaw(
                " Test Law ",
                "全国人民代表大会",
                LocalDate.of(2026, 8, 19),
                ValidityStatus.ACTIVE,
                List.of(chapter),
                List.of(article),
                "user-1");

        LawDocument storedLaw = lawRepository.findById(creation.law().getId()).orElseThrow();
        ContentVersionDocument storedC1 = contentVersionRepository
                .findByLawIdAndSeq(storedLaw.getId(), 1)
                .orElseThrow();
        assertThat(storedLaw.getName()).isEqualTo("Test Law");
        assertThat(storedLaw.getNormalizedName()).isEqualTo("test law");
        assertThat(storedLaw.getCurrentContentVersionId()).isEqualTo(storedC1.getId());
        assertThat(storedC1.getSemanticArticlesSnapshot()).hasSize(1);
        assertThat(storedC1.getSemanticArticlesSnapshot().getFirst().getArticleId())
                .isEqualTo(article.articleId());
        assertThat(storedLaw.getStructure().getFirst().getArticleIds())
                .containsExactly(article.articleId());
    }

    @Test
    void rejectsStructureReferencesMissingFromInitialSnapshot() {
        LawCreationService service = new LawCreationService(
                lawRepository, contentVersionRepository, mongoTemplate);
        LawStructureNode chapter = new LawStructureNode(
                "chapter-1",
                LawStructureNodeType.CHAPTER,
                "第一章 总则",
                null,
                0,
                List.of("missing-article"));

        assertThatThrownBy(() -> service.createInitialLaw(
                        "结构引用测试法",
                        "制定机关",
                        LocalDate.of(2026, 8, 19),
                        ValidityStatus.ACTIVE,
                        List.of(chapter),
                        List.of(new NewArticleDraft("第一条", "正文", 0)),
                        "user-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在的articleId");
        assertThat(lawRepository.findByNormalizedName("结构引用测试法")).isEmpty();
        assertThat(mongoTemplate.count(new Query(), ContentVersionDocument.class)).isZero();
    }

    @Test
    void databaseUniqueIndexRejectsNormalizedNameDuplicateEvenWithoutServicePrecheck() {
        lawRepository.insert(law("law-1", " Test Law ", false));

        assertThatThrownBy(() -> lawRepository.insert(law("law-2", "test law", false)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void serviceReturnsConflictForNormalizedNameDuplicate() {
        LawCreationService service = new LawCreationService(
                lawRepository, contentVersionRepository, mongoTemplate);
        service.createInitialLaw(
                " Test Law ",
                "制定机关",
                LocalDate.of(2026, 8, 19),
                ValidityStatus.ACTIVE,
                List.of(),
                List.of(new NewArticleDraft("第一条", "正文", 0)),
                "user-1");

        assertThatThrownBy(() -> service.createInitialLaw(
                        "test law",
                        "制定机关",
                        LocalDate.of(2026, 8, 19),
                        ValidityStatus.ACTIVE,
                        List.of(),
                        List.of(new NewArticleDraft("第一条", "正文", 0)),
                        "user-1"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(LawErrorCodes.NAME_ALREADY_EXISTS);
    }

    @Test
    void softDeletedLawStillReservesNormalizedName() {
        lawRepository.insert(law("law-1", " Test Law ", true));

        assertThatThrownBy(() -> lawRepository.insert(law("law-2", "TEST LAW", false)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void databaseUniqueIndexRejectsDuplicateContentSequence() {
        contentVersionRepository.insert(version(
                "c1", "law-1", 1, List.of(ArticleSnapshot.createNew("第一条", "旧正文", 0))));

        assertThatThrownBy(() -> contentVersionRepository.insert(version(
                        "c1-duplicate",
                        "law-1",
                        1,
                        List.of(ArticleSnapshot.createNew("第二条", "另一正文", 0)))))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void c2KeepsStableArticleIdentityWithoutOverwritingC1() {
        ArticleSnapshot c1Article = ArticleSnapshot.createNew("第一条", "旧正文", 0);
        ContentVersionDocument c1 = version("c1", "law-1", 1, List.of(c1Article));
        contentVersionRepository.insert(c1);

        ArticleSnapshot changedArticle = ArticleSnapshot.carryForward(
                c1Article.getArticleId(), "第一条", "新正文", 0);
        ArticleSnapshot addedArticle = ArticleSnapshot.createNew("第二条", "新增正文", 1);
        contentVersionRepository.insert(version(
                "c2", "law-1", 2, List.of(changedArticle, addedArticle)));

        ContentVersionDocument storedC1 = contentVersionRepository.findById("c1").orElseThrow();
        ContentVersionDocument storedC2 = contentVersionRepository.findById("c2").orElseThrow();
        assertThat(storedC1.getSemanticArticlesSnapshot().getFirst().getBody()).isEqualTo("旧正文");
        assertThat(storedC2.getSemanticArticlesSnapshot().getFirst().getBody()).isEqualTo("新正文");
        assertThat(storedC2.getSemanticArticlesSnapshot().getFirst().getArticleId())
                .isEqualTo(c1Article.getArticleId());
        assertThat(addedArticle.getArticleId()).isNotEqualTo(c1Article.getArticleId());
        assertThat(storedC1.getSemanticArticlesSnapshot().getFirst().getNumber())
                .isEqualTo(storedC2.getSemanticArticlesSnapshot().getFirst().getNumber());
    }

    @Test
    void lawAuditIsAppendOnlyAndReturnedNewestFirst() {
        Instant firstTime = Instant.parse("2026-08-19T01:00:00Z");
        Instant secondTime = Instant.parse("2026-08-19T02:00:00Z");
        lawAuditRepository.insert(new LawAuditDocument(
                "audit-1",
                "law-1",
                LawAuditType.BASE_INFO,
                Map.of("name", "A"),
                Map.of("name", "B"),
                "user-1",
                firstTime));
        lawAuditRepository.insert(new LawAuditDocument(
                "audit-2",
                "law-1",
                LawAuditType.STRUCTURE,
                Map.of("title", "第一章"),
                Map.of("title", "总则"),
                "user-1",
                secondTime));

        assertThat(lawAuditRepository.findByLawIdOrderByOperatedAtDesc("law-1"))
                .extracting(LawAuditDocument::getId)
                .containsExactly("audit-2", "audit-1");
        assertThat(Arrays.stream(ContentVersionRepository.class.getMethods())
                        .map(java.lang.reflect.Method::getName))
                .doesNotContain("save", "delete", "deleteById");
        assertThat(Arrays.stream(LawAuditRepository.class.getMethods())
                        .map(java.lang.reflect.Method::getName))
                .doesNotContain("save", "delete", "deleteById");
    }

    private static LawDocument law(String id, String name, boolean deleted) {
        Instant now = Instant.parse("2026-08-19T00:00:00Z");
        LawDocument law = LawDocument.createInitial(
                id,
                name,
                "制定机关",
                LocalDate.of(2026, 8, 19),
                ValidityStatus.ACTIVE,
                List.of(),
                "content-" + id,
                now);
        if (deleted) {
            law.markDeleted(now.plusSeconds(60));
        }
        return law;
    }

    private static ContentVersionDocument version(
            String id,
            String lawId,
            int seq,
            List<ArticleSnapshot> articles) {
        return new ContentVersionDocument(
                id,
                lawId,
                seq,
                articles,
                "user-1",
                Instant.parse("2026-08-19T00:00:00Z").plusSeconds(seq));
    }
}
