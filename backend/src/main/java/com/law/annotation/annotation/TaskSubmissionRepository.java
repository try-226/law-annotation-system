package com.law.annotation.annotation;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface TaskSubmissionRepository extends MongoRepository<TaskSubmissionDocument, String> {

    boolean existsByTaskIdAndSubmissionNo(String taskId, int submissionNo);
}
