package com.law.annotation.law;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface LawAuditRepository extends Repository<LawAuditDocument, String> {

    <S extends LawAuditDocument> S insert(S audit);

    Optional<LawAuditDocument> findById(String id);

    List<LawAuditDocument> findByLawIdOrderByOperatedAtDesc(String lawId);
}
