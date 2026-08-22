package com.law.annotation.field;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface FieldDefinitionRepository extends MongoRepository<FieldDefinitionDocument, String> {

    boolean existsByName(String name);
}
