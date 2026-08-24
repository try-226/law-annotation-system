package com.law.annotation.law;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Coordinates task creation and law mutations through one MongoDB atomic claim.
 */
@Component
public class LawOperationCoordinator {

    static final String OPERATION_TOKEN_FIELD = "operationToken";
    static final String OPERATION_EXPIRES_AT_FIELD = "operationExpiresAt";

    private static final Duration OPERATION_LEASE = Duration.ofMinutes(5);

    private final MongoTemplate mongoTemplate;

    public LawOperationCoordinator(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public <T> T withVisibleLaw(
            String lawId,
            Supplier<? extends RuntimeException> conflict,
            Function<String, T> operation) {
        return withLaw(lawId, Criteria.where("deletedAt").is(null), conflict, operation);
    }

    public <T> T withDeletedLaw(
            String lawId,
            Supplier<? extends RuntimeException> conflict,
            Function<String, T> operation) {
        return withLaw(
                lawId,
                Criteria.where("deletedAt").exists(true).ne(null),
                conflict,
                operation);
    }

    public void renewVisibleLaw(
            String lawId,
            String token,
            Supplier<? extends RuntimeException> conflict) {
        Instant now = Instant.now();
        long matched = mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id")
                                .is(lawId)
                                .and("deletedAt").is(null)
                                .and(OPERATION_TOKEN_FIELD).is(token)),
                        new Update().set(
                                OPERATION_EXPIRES_AT_FIELD,
                                now.plus(OPERATION_LEASE)),
                        LawDocument.class)
                .getMatchedCount();
        if (matched != 1) {
            throw conflict.get();
        }
    }

    private <T> T withLaw(
            String lawId,
            Criteria deletionState,
            Supplier<? extends RuntimeException> conflict,
            Function<String, T> operation) {
        String token = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Criteria unlocked = new Criteria().orOperator(
                Criteria.where(OPERATION_TOKEN_FIELD).exists(false),
                Criteria.where(OPERATION_TOKEN_FIELD).is(null),
                Criteria.where(OPERATION_EXPIRES_AT_FIELD).exists(false),
                Criteria.where(OPERATION_EXPIRES_AT_FIELD).is(null),
                Criteria.where(OPERATION_EXPIRES_AT_FIELD).lte(now));
        LawDocument locked = mongoTemplate.findAndModify(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(lawId),
                        deletionState,
                        unlocked)),
                new Update()
                        .set(OPERATION_TOKEN_FIELD, token)
                        .set(OPERATION_EXPIRES_AT_FIELD, now.plus(OPERATION_LEASE)),
                FindAndModifyOptions.options().returnNew(true),
                LawDocument.class);
        if (locked == null) {
            throw conflict.get();
        }
        RuntimeException operationFailure = null;
        try {
            return operation.apply(token);
        } catch (RuntimeException exception) {
            operationFailure = exception;
            throw exception;
        } finally {
            try {
                mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id")
                                .is(lawId)
                                .and(OPERATION_TOKEN_FIELD).is(token)),
                        new Update()
                                .unset(OPERATION_TOKEN_FIELD)
                                .unset(OPERATION_EXPIRES_AT_FIELD),
                        LawDocument.class);
            } catch (RuntimeException releaseFailure) {
                if (operationFailure != null) {
                    operationFailure.addSuppressed(releaseFailure);
                } else {
                    throw releaseFailure;
                }
            }
        }
    }
}
