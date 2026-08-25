package com.law.annotation.annotation;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TaskSubmissionRepository extends MongoRepository<TaskSubmissionDocument, String> {

    Optional<TaskSubmissionDocument> findByTaskIdAndSubmissionNo(
            String taskId,
            int submissionNo);

    Optional<TaskSubmissionDocument> findBySourceReviewRoundId(String sourceReviewRoundId);

    Optional<TaskSubmissionDocument> findTopByTaskIdOrderBySubmissionNoDesc(String taskId);
}
