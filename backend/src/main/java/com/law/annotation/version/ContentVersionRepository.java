package com.law.annotation.version;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface ContentVersionRepository extends Repository<ContentVersionDocument, String> {

    <S extends ContentVersionDocument> S insert(S contentVersion);

    Optional<ContentVersionDocument> findById(String id);

    Optional<ContentVersionDocument> findByLawIdAndSeq(String lawId, int seq);

    Optional<ContentVersionDocument> findTopByLawIdOrderBySeqDesc(String lawId);

    List<ContentVersionDocument> findByLawIdOrderBySeqAsc(String lawId);

    List<ContentVersionDocument> findByIdIn(Collection<String> ids);
}
