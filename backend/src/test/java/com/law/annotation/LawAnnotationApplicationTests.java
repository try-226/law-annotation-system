package com.law.annotation;

import com.law.annotation.bootstrap.BootstrapAdminRunner;
import com.law.annotation.user.UserIndexInitializer;
import com.law.annotation.user.UserRepository;
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
    private MongoTemplate mongoTemplate;

    @MockitoBean
    private UserIndexInitializer userIndexInitializer;

    @MockitoBean
    private BootstrapAdminRunner bootstrapAdminRunner;

    @Test
    void contextLoads() {
    }
}
