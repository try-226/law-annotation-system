package com.law.annotation.annotation;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface TaskDraftRepository extends MongoRepository<TaskDraftDocument, String> {
}
