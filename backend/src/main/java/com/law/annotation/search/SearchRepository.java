package com.law.annotation.search;

import com.law.annotation.common.enums.ItemType;
import com.law.annotation.law.LawDocument;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
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

    public List<LawDocument> findVisibleLawsMatching(
            Pattern literalPattern,
            SearchScope scope) {
        List<AggregationOperation> operations = new ArrayList<>();
        operations.add(Aggregation.match(Criteria.where("deletedAt").is(null)));
        operations.add(Aggregation.lookup(
                "content_versions",
                "currentContentVersionId",
                "_id",
                "currentContent"));
        operations.add(Aggregation.unwind("currentContent"));
        operations.add(rawMatch(new Document(
                "$expr",
                new Document("$eq", List.of("$currentContent.lawId", "$_id")))));

        if (scope.includesAnnotation()) {
            operations.add(Aggregation.lookup(
                    "annotation_versions",
                    "currentAnnotationVersionId",
                    "_id",
                    "currentAnnotation"));
            operations.add(Aggregation.unwind("currentAnnotation", true));
            operations.add(context -> new Document(
                    "$addFields",
                    new Document(
                            "annotationArticleValues",
                            new Document(
                                    "$objectToArray",
                                    new Document(
                                            "$ifNull",
                                            List.of(
                                                    "$currentAnnotation.articleResults",
                                                    new Document()))))));
        }

        operations.add(rawMatch(scopeMatch(literalPattern, scope)));
        operations.add(Aggregation.sort(LAW_ORDER));
        return mongoTemplate.aggregate(
                        Aggregation.newAggregation(operations),
                        mongoTemplate.getCollectionName(LawDocument.class),
                        LawDocument.class)
                .getMappedResults();
    }

    private static AggregationOperation rawMatch(Document criteria) {
        return context -> new Document("$match", criteria);
    }

    private static Document scopeMatch(Pattern pattern, SearchScope scope) {
        return switch (scope) {
            case LAW_TEXT -> lawTextMatch(pattern);
            case ANNOTATION -> annotationMatch(pattern);
            case ALL -> new Document(
                    "$or",
                    List.of(lawTextMatch(pattern), annotationMatch(pattern)));
        };
    }

    private static Document lawTextMatch(Pattern pattern) {
        return new Document("$or", List.of(
                new Document("name", pattern),
                new Document("issuingAuthority", pattern),
                new Document("structure.title", pattern),
                new Document("currentContent.semanticArticlesSnapshot.number", pattern),
                new Document("currentContent.semanticArticlesSnapshot.body", pattern)));
    }

    private static Document annotationMatch(Pattern pattern) {
        List<Document> annotationFields = new ArrayList<>(List.of(
                new Document("currentAnnotation.overallResult.lawCategory", pattern),
                new Document("currentAnnotation.overallResult.overallKeywords", pattern),
                new Document("currentAnnotation.overallResult.summary", pattern),
                new Document("currentAnnotation.overallResult.overallNote", pattern),
                new Document("annotationArticleValues.v.keywords", pattern),
                new Document("annotationArticleValues.v.subjects", pattern),
                new Document("annotationArticleValues.v.legalLiability", pattern),
                new Document("annotationArticleValues.v.annotationNote", pattern)));
        List<String> itemTypes = Arrays.stream(ItemType.values())
                .filter(itemType -> pattern.matcher(itemTypeLabel(itemType)).find())
                .map(Enum::name)
                .toList();
        if (!itemTypes.isEmpty()) {
            annotationFields.add(new Document(
                    "annotationArticleValues.v.itemType",
                    new Document("$in", itemTypes)));
        }

        return new Document("$and", List.of(
                new Document("$expr", new Document("$and", List.of(
                        new Document("$eq", List.of(
                                "$currentAnnotation._id",
                                "$currentAnnotationVersionId")),
                        new Document("$eq", List.of(
                                "$currentAnnotation.lawId",
                                "$_id")),
                        new Document("$eq", List.of(
                                "$currentAnnotation.contentVersionId",
                                "$currentContentVersionId"))))),
                new Document("$or", annotationFields)));
    }

    private static String itemTypeLabel(ItemType itemType) {
        return switch (itemType) {
            case DEFINITION -> "定义解释类";
            case RIGHTS_DUTIES -> "权利义务类";
            case AUTHORITY_DUTY -> "授权职责类";
            case PROHIBITION_RESTRICTION -> "禁止限制类";
            case PROCEDURE -> "程序规则类";
            case LIABILITY -> "法律责任类";
            case OTHER -> "其他";
        };
    }
}
