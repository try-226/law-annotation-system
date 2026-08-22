package com.law.annotation;

import com.law.annotation.bootstrap.BootstrapAdminRunner;
import com.law.annotation.field.FieldDefinitionIndexInitializer;
import com.law.annotation.field.FieldDefinitionRepository;
import com.law.annotation.law.LawAuditRepository;
import com.law.annotation.law.LawDomainIndexInitializer;
import com.law.annotation.law.LawRepository;
import com.law.annotation.user.UserIndexInitializer;
import com.law.annotation.user.UserRepository;
import com.law.annotation.version.ContentVersionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.data.mongodb.uri=mongodb://localhost:27017/law_annotation_test",
        "spring.data.mongodb.auto-index-creation=false"
})
@ImportAutoConfiguration(exclude = {
        MongoAutoConfiguration.class,
        MongoDataAutoConfiguration.class,
        MongoRepositoriesAutoConfiguration.class
})
class LawAnnotationApplicationTests {

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private LawRepository lawRepository;

    @MockitoBean
    private LawAuditRepository lawAuditRepository;

    @MockitoBean
    private ContentVersionRepository contentVersionRepository;

    @MockitoBean
    private FieldDefinitionRepository fieldDefinitionRepository;

    @MockitoBean
    private MongoTemplate mongoTemplate;

    @MockitoBean
    private UserIndexInitializer userIndexInitializer;

    @MockitoBean
    private LawDomainIndexInitializer lawDomainIndexInitializer;

    @MockitoBean
    private FieldDefinitionIndexInitializer fieldDefinitionIndexInitializer;

    @MockitoBean
    private BootstrapAdminRunner bootstrapAdminRunner;

    @Test
    void contextLoads() {
    }
}
