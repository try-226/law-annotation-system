package com.law.annotation.task;

import com.law.annotation.common.enums.TaskState;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface TaskRepository extends MongoRepository<TaskDocument, String> {

    boolean existsByLawIdAndTaskStateIn(String lawId, Collection<TaskState> states);

    boolean existsByAnnotatorIdAndTaskStateIn(String annotatorId, Collection<TaskState> states);

    @Query(
            value = "{ 'taskState': { $in: ?0 } }",
            fields = "{ 'lawId': 1, 'taskType': 1, 'taskState': 1 }")
    List<TaskStatusProjection> findStatusesByTaskStateIn(Collection<TaskState> states);

    @Query(
            value = "{ 'lawId': { $in: ?0 }, 'taskState': { $in: ?1 } }",
            fields = "{ 'lawId': 1, 'taskType': 1, 'taskState': 1 }")
    List<TaskStatusProjection> findStatusesByLawIdInAndTaskStateIn(
            Collection<String> lawIds,
            Collection<TaskState> states);

    long countByTaskStateInAndLawIdIn(
            Collection<TaskState> states,
            Collection<String> lawIds);

    long countByTaskStateAndLawIdIn(
            TaskState state,
            Collection<String> lawIds);

    List<TaskDocument> findTop10ByTaskStateAndLawIdInOrderByCreatedAtDescTaskIdDesc(
            TaskState state,
            Collection<String> lawIds);

    boolean existsByAnnotatorIdOrCreatedByOrCanceledBy(
            String annotatorId, String createdBy, String canceledBy);

    Optional<TaskDocument> findByTaskIdAndAnnotatorId(String taskId, String annotatorId);
}
