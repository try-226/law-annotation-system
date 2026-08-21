package com.law.annotation.law;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface LawRepository extends MongoRepository<LawDocument, String> {

    Optional<LawDocument> findByNormalizedName(String normalizedName);

    boolean existsByNormalizedName(String normalizedName);

    Page<LawDocument> findByDeletedAtIsNull(Pageable pageable);

    Page<LawDocument> findByDeletedAtIsNullAndNormalizedNameContaining(
            String normalizedName,
            Pageable pageable);
}
