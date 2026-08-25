package com.law.annotation.review;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReviewRoundRepository extends MongoRepository<ReviewRoundDocument, String> {

    Optional<ReviewRoundDocument> findByTaskIdAndSourceSubmissionId(
            String taskId,
            String sourceSubmissionId);

    Optional<ReviewRoundDocument> findByTaskIdAndRoundNo(String taskId, int roundNo);

    boolean existsByReviewerIdAndCompletedAtIsNull(String reviewerId);

    boolean existsByReviewerId(String reviewerId);
}
