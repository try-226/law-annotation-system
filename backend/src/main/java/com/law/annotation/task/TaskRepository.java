package com.law.annotation.task;

import com.law.annotation.common.enums.TaskState;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TaskRepository extends MongoRepository<TaskDocument, String> {

    boolean existsByLawIdAndTaskStateIn(String lawId, Collection<TaskState> states);

    boolean existsByAnnotatorIdAndTaskStateIn(String annotatorId, Collection<TaskState> states);

    boolean existsByAnnotatorIdOrCreatedByOrCanceledBy(
            String annotatorId, String createdBy, String canceledBy);

    List<TaskDocument> findByLawIdInAndTaskStateIn(
            Collection<String> lawIds,
            Collection<TaskState> states);

    Optional<TaskDocument> findByTaskIdAndAnnotatorId(String taskId, String annotatorId);
}
