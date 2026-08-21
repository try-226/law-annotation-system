package com.law.annotation.law;

import com.law.annotation.version.ContentVersionDocument;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LawDomainIndexInitializer implements ApplicationRunner {

    public static final String NORMALIZED_NAME_INDEX = "uk_laws_normalized_name";
    public static final String CONTENT_VERSION_SEQUENCE_INDEX = "uk_content_versions_law_seq";
    public static final String AUDIT_HISTORY_INDEX = "ix_law_audits_law_operated_at";

    private final MongoTemplate mongoTemplate;

    public LawDomainIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        mongoTemplate.indexOps(LawDocument.class).createIndex(
                new Index()
                        .on("normalizedName", Sort.Direction.ASC)
                        .unique()
                        .named(NORMALIZED_NAME_INDEX));
        mongoTemplate.indexOps(ContentVersionDocument.class).createIndex(
                new Index()
                        .on("lawId", Sort.Direction.ASC)
                        .on("seq", Sort.Direction.ASC)
                        .unique()
                        .named(CONTENT_VERSION_SEQUENCE_INDEX));
        mongoTemplate.indexOps(LawAuditDocument.class).createIndex(
                new Index()
                        .on("lawId", Sort.Direction.ASC)
                        .on("operatedAt", Sort.Direction.DESC)
                        .named(AUDIT_HISTORY_INDEX));
    }
}
