package com.law.annotation.law;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class LawSearchRepository {

    private final MongoTemplate mongoTemplate;

    public LawSearchRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Page<LawDocument> search(LawSearchFilter filter, Pageable pageable) {
        if (requiresActiveTask(filter.displayStatus()) && filter.includeLawIds().isEmpty()) {
            return Page.empty(pageable);
        }

        List<Criteria> conditions = new ArrayList<>();
        conditions.add(Criteria.where("deletedAt").is(null));
        if (filter.normalizedName() != null) {
            conditions.add(Criteria.where("normalizedName")
                    .regex(Pattern.quote(filter.normalizedName())));
        }
        if (filter.validityStatus() != null) {
            conditions.add(Criteria.where("validityStatus").is(filter.validityStatus()));
        }
        addDisplayStatusCriteria(conditions, filter);

        Query query = Query.query(new Criteria().andOperator(conditions));
        long total = mongoTemplate.count(query, LawDocument.class);
        List<LawDocument> content = mongoTemplate.find(query.with(pageable), LawDocument.class);
        return new PageImpl<>(content, pageable, total);
    }

    private static void addDisplayStatusCriteria(
            List<Criteria> conditions,
            LawSearchFilter filter) {
        LawDisplayStatus status = filter.displayStatus();
        if (status == null) {
            return;
        }
        if (requiresActiveTask(status)) {
            conditions.add(Criteria.where("_id").in(filter.includeLawIds()));
            return;
        }
        if (!filter.excludeLawIds().isEmpty()) {
            conditions.add(Criteria.where("_id").nin(filter.excludeLawIds()));
        }
        switch (status) {
            case PENDING_REVISION -> conditions.add(Criteria.where("pendingRevision").is(true));
            case COMPLETED -> {
                conditions.add(Criteria.where("pendingRevision").is(false));
                conditions.add(Criteria.where("currentAnnotationVersionId").ne(null));
            }
            case UNANNOTATED -> {
                conditions.add(Criteria.where("pendingRevision").is(false));
                conditions.add(Criteria.where("currentAnnotationVersionId").is(null));
            }
            default -> throw new IllegalArgumentException("不支持的法律展示状态筛选: " + status);
        }
    }

    static boolean requiresActiveTask(LawDisplayStatus status) {
        return status == LawDisplayStatus.ANNOTATING
                || status == LawDisplayStatus.REVISING
                || status == LawDisplayStatus.PENDING_REVIEW
                || status == LawDisplayStatus.PARTIALLY_REJECTED
                || status == LawDisplayStatus.PENDING_REREVIEW;
    }
}
