package com.law.annotation.law;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface LawRepository extends MongoRepository<LawDocument, String> {

    Optional<LawDocument> findByNormalizedName(String normalizedName);

    Optional<LawDocument> findFirstByNormalizedNameAndIdNot(String normalizedName, String id);

    boolean existsByNormalizedName(String normalizedName);

    List<LawDocument> findAllByDeletedAtIsNull();

    @Query(
            value = "{ 'deletedAt': null }",
            fields = "{ '_id': 1, 'name': 1, 'currentContentVersionId': 1, "
                    + "'currentAnnotationVersionId': 1, 'pendingRevision': 1 }")
    List<LawDashboardProjection> findDashboardLaws();

    Page<LawDocument> findByDeletedAtIsNull(Pageable pageable);

    Page<LawDocument> findByDeletedAtIsNullAndNormalizedNameContaining(
            String normalizedName,
            Pageable pageable);

    Page<LawDocument> findByDeletedAtIsNotNull(Pageable pageable);

    Page<LawDocument> findByDeletedAtIsNotNullAndNormalizedNameContaining(
            String normalizedName,
            Pageable pageable);
}
