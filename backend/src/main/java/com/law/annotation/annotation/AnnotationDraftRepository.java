package com.law.annotation.annotation;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AnnotationDraftRepository
        extends MongoRepository<AnnotationDraftDocument, String> {
}
