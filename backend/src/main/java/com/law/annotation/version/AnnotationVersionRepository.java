package com.law.annotation.version;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface AnnotationVersionRepository extends Repository<AnnotationVersionDocument, String> {

    <S extends AnnotationVersionDocument> S insert(S version);

    Optional<AnnotationVersionDocument> findById(String id);

    List<AnnotationVersionDocument> findByIdIn(Collection<String> ids);

    Optional<AnnotationVersionDocument> findBySourceTaskId(String sourceTaskId);

    Optional<AnnotationVersionDocument> findTopByLawIdOrderBySeqDesc(String lawId);
}
