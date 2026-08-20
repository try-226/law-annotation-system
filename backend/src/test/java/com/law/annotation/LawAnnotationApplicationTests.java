package com.law.annotation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.data.mongodb.uri=mongodb://localhost:27017/law_annotation_test",
        "spring.data.mongodb.auto-index-creation=false"
})
class LawAnnotationApplicationTests {

    @Test
    void contextLoads() {
    }
}
