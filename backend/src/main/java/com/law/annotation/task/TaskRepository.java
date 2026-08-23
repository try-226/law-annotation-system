package com.law.annotation.task;

import com.law.annotation.common.enums.TaskState;
import java.util.Collection;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TaskRepository extends MongoRepository<TaskDocument, String> {

    boolean existsByLawIdAndTaskStateIn(String lawId, Collection<TaskState> states);

    boolean existsByAnnotatorIdAndTaskStateIn(String annotatorId, Collection<TaskState> states);

    boolean existsByAnnotatorId(String annotatorId);
}
