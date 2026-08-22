package com.law.annotation.field;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FieldConfigRepository extends MongoRepository<FieldConfigDocument, String> {

    List<FieldConfigDocument> findAllByFieldKeyIn(Collection<String> fieldKeys);

    Optional<FieldConfigDocument> findByFieldKey(String fieldKey);
}
