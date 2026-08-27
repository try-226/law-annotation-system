package com.law.annotation.law;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface LawRepository extends MongoRepository<LawDocument, String> {

    Optional<LawDocument> findByNormalizedName(String normalizedName);

    Optional<LawDocument> findFirstByNormalizedNameAndIdNot(String normalizedName, String id);

    boolean existsByNormalizedName(String normalizedName);

    List<LawDocument> findAllByDeletedAtIsNull();

    Page<LawDocument> findByDeletedAtIsNull(Pageable pageable);

    Page<LawDocument> findByDeletedAtIsNullAndNormalizedNameContaining(
            String normalizedName,
            Pageable pageable);

    Page<LawDocument> findByDeletedAtIsNotNull(Pageable pageable);

    Page<LawDocument> findByDeletedAtIsNotNullAndNormalizedNameContaining(
            String normalizedName,
            Pageable pageable);
}
