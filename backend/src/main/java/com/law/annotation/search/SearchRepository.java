package com.law.annotation.search;

import com.law.annotation.law.LawDocument;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class SearchRepository {

    private static final Sort LAW_ORDER = Sort.by(
            Sort.Order.desc("updatedAt"),
            Sort.Order.desc("_id"));

    private final MongoTemplate mongoTemplate;

    public SearchRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<LawDocument> findVisibleLaws() {
        Query query = Query.query(Criteria.where("deletedAt").is(null)).with(LAW_ORDER);
        return mongoTemplate.find(query, LawDocument.class);
    }
}
