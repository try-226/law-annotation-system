package com.law.annotation.task;

import com.law.annotation.common.enums.TaskState;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TaskRepository extends MongoRepository<TaskDocument, String> {

    boolean existsByLawIdAndTaskStateIn(String lawId, Collection<TaskState> states);

    List<TaskDocument> findByLawIdInAndTaskStateIn(
            Collection<String> lawIds,
            Collection<TaskState> states);

    default List<TaskDocument> findUnfinishedByLawIds(Collection<String> lawIds) {
        if (lawIds.isEmpty()) {
            return List.of();
        }
        return findByLawIdInAndTaskStateIn(lawIds, TaskStateRules.UNFINISHED_STATES);
    }

    boolean existsByAnnotatorIdAndTaskStateIn(String annotatorId, Collection<TaskState> states);

    boolean existsByAnnotatorIdOrCreatedByOrCanceledBy(
            String annotatorId, String createdBy, String canceledBy);

    Optional<TaskDocument> findByTaskIdAndAnnotatorId(String taskId, String annotatorId);
}
