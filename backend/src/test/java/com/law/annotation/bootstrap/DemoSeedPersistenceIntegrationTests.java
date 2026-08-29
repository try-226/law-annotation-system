package com.law.annotation.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.law.annotation.common.enums.Role;
import com.law.annotation.law.LawCreationService;
import com.law.annotation.law.LawDocument;
import com.law.annotation.law.LawDomainIndexInitializer;
import com.law.annotation.law.LawDomainRules;
import com.law.annotation.law.LawRepository;
import com.law.annotation.user.UserBusinessUsagePort;
import com.law.annotation.user.UserDocument;
import com.law.annotation.user.UserFieldValidator;
import com.law.annotation.user.UserIndexInitializer;
import com.law.annotation.user.UserRepository;
import com.law.annotation.user.UserService;
import com.law.annotation.version.ContentVersionDocument;
import com.law.annotation.version.ContentVersionRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class DemoSeedPersistenceIntegrationTests {

    private static MongoServer mongoServer;
    private static MongoClient mongoClient;
    private static MongoTemplate mongoTemplate;
    private static UserRepository userRepository;
    private static LawRepository lawRepository;
    private static ContentVersionRepository contentVersionRepository;
    private static BootstrapSeedStateRepository seedStateRepository;
    private static UserService userService;
    private static LawCreationService lawCreationService;

    @BeforeAll
    static void startMongo() {
        mongoServer = new MongoServer(new MemoryBackend());
        mongoClient = MongoClients.create(mongoServer.bindAndGetConnectionString());
        mongoTemplate = new MongoTemplate(mongoClient, "demo_seed_test");
        MongoRepositoryFactory factory = new MongoRepositoryFactory(mongoTemplate);
        userRepository = factory.getRepository(UserRepository.class);
        lawRepository = factory.getRepository(LawRepository.class);
        contentVersionRepository = factory.getRepository(ContentVersionRepository.class);
        seedStateRepository = factory.getRepository(BootstrapSeedStateRepository.class);
        new UserIndexInitializer(mongoTemplate).run(new DefaultApplicationArguments());
        new LawDomainIndexInitializer(mongoTemplate).run(new DefaultApplicationArguments());
        userService = new UserService(
                userRepository,
                mongoTemplate,
                new BCryptPasswordEncoder(4),
                new UserFieldValidator(),
                mock(UserBusinessUsagePort.class));
        lawCreationService = new LawCreationService(
                lawRepository, contentVersionRepository, mongoTemplate);
    }

    @AfterAll
    static void stopMongo() {
        mongoClient.close();
        mongoServer.shutdown();
    }

    @BeforeEach
    void clearDocuments() {
        mongoTemplate.remove(new Query(), UserDocument.class);
        mongoTemplate.remove(new Query(), LawDocument.class);
        mongoTemplate.remove(new Query(), ContentVersionDocument.class);
        mongoTemplate.remove(new Query(), BootstrapSeedStateDocument.class);
    }

    @Test
    void disabledSeedCreatesNothing() throws Exception {
        runner(false).run(new DefaultApplicationArguments());

        assertThat(userRepository.count()).isZero();
        assertThat(lawRepository.count()).isZero();
        assertThat(contentVersionRepository.findByLawIdOrderBySeqAsc("missing")).isEmpty();
        assertThat(seedStateRepository.count()).isZero();
    }

    @Test
    void enabledSeedCreatesOneAnnotatorOneLawAndOneC1AndRestartIsIdempotent() throws Exception {
        userService.createUser("系统管理员", "admin", "admin123", Role.ADMIN);
        DemoSeedRunner runner = runner(true);

        runner.run(new DefaultApplicationArguments());
        UserDocument annotator = userRepository.findByNormalizedAccount("annotator").orElseThrow();
        LawDocument law = lawRepository.findByNormalizedName(
                LawDomainRules.normalizeLawName(DemoSeedData.LAW_NAME)).orElseThrow();
        ContentVersionDocument c1 = contentVersionRepository.findById(
                law.getCurrentContentVersionId()).orElseThrow();
        String passwordHash = annotator.getPasswordHash();

        runner.run(new DefaultApplicationArguments());

        assertThat(userRepository.count()).isEqualTo(2);
        assertThat(annotator.getRole()).isEqualTo(Role.ANNOTATOR);
        assertThat(annotator.isEnabled()).isTrue();
        assertThat(userRepository.findById(annotator.getId()).orElseThrow().getPasswordHash())
                .isEqualTo(passwordHash);
        assertThat(lawRepository.count()).isEqualTo(1);
        assertThat(seedStateRepository.findById(DemoSeedRunner.DEMO_LAW_MARKER)
                .orElseThrow()
                .getResourceId()).isEqualTo(law.getId());
        assertThat(c1.getSeq()).isEqualTo(1);
        assertThat(c1.getSemanticArticlesSnapshot()).hasSize(8);
        assertThat(law.getStructure()).hasSize(2);
        assertThat(law.getCurrentAnnotationVersionId()).isNull();
        assertThat(count("content_versions")).isEqualTo(1);
        assertThat(count("law_audits")).isZero();
        assertThat(count("tasks")).isZero();
        assertThat(count("task_submissions")).isZero();
        assertThat(count("review_rounds")).isZero();
        assertThat(count("annotation_versions")).isZero();
    }

    @Test
    void renamedDemoLawKeepsStableIdAndDoesNotCreateAnotherLawOrContentVersion() throws Exception {
        userService.createUser("系统管理员", "admin", "admin123", Role.ADMIN);
        DemoSeedRunner runner = runner(true);
        runner.run(new DefaultApplicationArguments());
        LawDocument original = lawRepository.findByNormalizedName(
                LawDomainRules.normalizeLawName(DemoSeedData.LAW_NAME)).orElseThrow();
        long lawCount = lawRepository.count();
        long contentVersionCount = count("content_versions");
        String renamed = "人工修改后的演示法律名称";

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(original.getId())),
                new Update()
                        .set("name", renamed)
                        .set("normalizedName", LawDomainRules.normalizeLawName(renamed)),
                LawDocument.class);

        runner.run(new DefaultApplicationArguments());

        LawDocument retained = lawRepository.findById(original.getId()).orElseThrow();
        assertThat(retained.getName()).isEqualTo(renamed);
        assertThat(lawRepository.count()).isEqualTo(lawCount);
        assertThat(count("content_versions")).isEqualTo(contentVersionCount);
        assertThat(lawRepository.findByNormalizedName(
                LawDomainRules.normalizeLawName(DemoSeedData.LAW_NAME))).isEmpty();
        assertThat(seedStateRepository.findById(DemoSeedRunner.DEMO_LAW_MARKER)
                .orElseThrow()
                .getResourceId()).isEqualTo(original.getId());
    }

    @Test
    void stableStateWhoseLawIsPhysicallyMissingFailsWithoutRecreatingIt() throws Exception {
        userService.createUser("系统管理员", "admin", "admin123", Role.ADMIN);
        DemoSeedRunner runner = runner(true);
        runner.run(new DefaultApplicationArguments());
        BootstrapSeedStateDocument state = seedStateRepository
                .findById(DemoSeedRunner.DEMO_LAW_MARKER)
                .orElseThrow();
        long contentVersionCount = count("content_versions");
        mongoTemplate.remove(
                Query.query(Criteria.where("_id").is(state.getResourceId())),
                LawDocument.class);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("稳定初始化状态指向的演示法律不存在")
                .hasMessageContaining("拒绝静默重建");
        assertThat(lawRepository.count()).isZero();
        assertThat(count("content_versions")).isEqualTo(contentVersionCount);
        assertThat(seedStateRepository.findById(DemoSeedRunner.DEMO_LAW_MARKER))
                .isPresent();
    }

    private static DemoSeedRunner runner(boolean enabled) {
        return new DemoSeedRunner(
                new DemoSeedProperties(enabled, "annotator", "annotator123"),
                userService,
                userRepository,
                lawCreationService,
                lawRepository,
                seedStateRepository);
    }

    private static long count(String collection) {
        if (!mongoTemplate.collectionExists(collection)) {
            return 0;
        }
        return mongoTemplate.getCollection(collection).countDocuments();
    }
}
