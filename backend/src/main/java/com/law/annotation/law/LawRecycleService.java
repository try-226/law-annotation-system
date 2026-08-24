package com.law.annotation.law;

import com.law.annotation.common.exception.ApiException;
import com.law.annotation.law.dto.LawDetailResponse;
import com.law.annotation.version.ContentVersionDocument;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LawRecycleService {

    private final LawRepository lawRepository;
    private final LawQueryService lawQueryService;
    private final MongoTemplate mongoTemplate;
    private final List<LawMutationGuard> mutationGuards;

    public LawRecycleService(
            LawRepository lawRepository,
            LawQueryService lawQueryService,
            MongoTemplate mongoTemplate,
            List<LawMutationGuard> mutationGuards) {
        this.lawRepository = lawRepository;
        this.lawQueryService = lawQueryService;
        this.mongoTemplate = mongoTemplate;
        this.mutationGuards = List.copyOf(mutationGuards);
    }

    public void deleteLaw(String lawId) {
        LawDocument law = lawQueryService.requireVisibleLaw(lawId);
        assertMutationAllowed(lawId);
        if (hasBusinessHistory(law)) {
            softDelete(law);
        } else {
            physicallyDelete(law);
        }
    }

    public LawDetailResponse restoreLaw(String lawId) {
        LawDocument law = lawQueryService.requireDeletedLaw(lawId);
        assertMutationAllowed(lawId);
        lawQueryService.requireCurrentVersion(law);
        lawRepository.findFirstByNormalizedNameAndIdNot(law.getNormalizedName(), law.getId())
                .ifPresent(conflict -> {
                    throw nameConflict();
                });

        Instant now = Instant.now();
        try {
            UpdateResult result = mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id")
                            .is(law.getId())
                            .and("deletedAt").is(law.getDeletedAt())
                            .and("updatedAt").is(law.getUpdatedAt())
                            .and("currentContentVersionId").is(law.getCurrentContentVersionId())),
                    new Update()
                            .set("deleted", false)
                            .unset("deletedAt")
                            .set("updatedAt", now),
                    LawDocument.class);
            requireUpdated(result);
        } catch (DuplicateKeyException exception) {
            throw nameConflict();
        }
        return lawQueryService.getDetail(lawId);
    }

    private boolean hasBusinessHistory(LawDocument law) {
        if (law.getCurrentAnnotationVersionId() != null) {
            return true;
        }
        Query lawIdQuery = Query.query(Criteria.where("lawId").is(law.getId()));
        if (mongoTemplate.exists(lawIdQuery, "tasks")) {
            return true;
        }
        if (mongoTemplate.exists(lawIdQuery, LawAuditDocument.class)) {
            return true;
        }
        return mongoTemplate.count(lawIdQuery, ContentVersionDocument.class) > 1;
    }

    private void softDelete(LawDocument law) {
        Instant now = Instant.now();
        UpdateResult result = mongoTemplate.updateFirst(
                currentLawQuery(law),
                new Update()
                        .set("deleted", true)
                        .set("deletedAt", now)
                        .set("updatedAt", now),
                LawDocument.class);
        requireUpdated(result);
    }

    private void physicallyDelete(LawDocument law) {
        DeleteResult deletedLaw = mongoTemplate.remove(currentLawQuery(law), LawDocument.class);
        if (deletedLaw.getDeletedCount() != 1) {
            throw versionConflict();
        }
        try {
            DeleteResult deletedVersion = mongoTemplate.remove(
                    Query.query(Criteria.where("_id")
                            .is(law.getCurrentContentVersionId())
                            .and("lawId").is(law.getId())),
                    ContentVersionDocument.class);
            if (deletedVersion.getDeletedCount() != 1) {
                throw versionConflict();
            }
        } catch (RuntimeException exception) {
            try {
                lawRepository.insert(law);
            } catch (RuntimeException compensationFailure) {
                exception.addSuppressed(compensationFailure);
            }
            throw exception;
        }
    }

    private void assertMutationAllowed(String lawId) {
        mutationGuards.forEach(guard -> guard.assertMutationAllowed(lawId));
    }

    private static Query currentLawQuery(LawDocument law) {
        return Query.query(Criteria.where("_id")
                .is(law.getId())
                .and("deletedAt").is(null)
                .and("updatedAt").is(law.getUpdatedAt())
                .and("currentContentVersionId").is(law.getCurrentContentVersionId()));
    }

    private static void requireUpdated(UpdateResult result) {
        if (result.getModifiedCount() != 1) {
            throw versionConflict();
        }
    }

    private static ApiException nameConflict() {
        return new ApiException(
                HttpStatus.CONFLICT,
                LawErrorCodes.NAME_ALREADY_EXISTS,
                "法律名称已被其他法律占用，不能恢复");
    }

    private static ApiException versionConflict() {
        return new ApiException(
                HttpStatus.CONFLICT,
                LawErrorCodes.VERSION_CONFLICT,
                "法律业务状态已发生变化，请刷新后重试");
    }
}
