package io.shinmen.app.tasker.repository.mongo;

import io.shinmen.app.tasker.model.TaskHistory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface TaskHistoryRepository extends MongoRepository<TaskHistory, String> {
    List<TaskHistory> findByTaskIdAndTimestampAfter(Long taskId, LocalDateTime timestamp);

    Page<TaskHistory> findByTaskIdOrderByTimestampDesc(Long taskId, Pageable pageable);

    List<TaskHistory> findByTaskIdOrderByTimestampAsc(Long taskId);

    List<TaskHistory> findByTaskIdAndTimestampAfterOrderByTimestampDesc(
        Long taskId,
        LocalDateTime since
    );

    @Query("{'taskId': ?0, 'timestamp': {$gte: ?1, $lte: ?2}}")
    List<TaskHistory> findByTaskIdAndTimestampBetween(
        Long taskId,
        LocalDateTime startDate,
        LocalDateTime endDate
    );

    @Query("{'taskId': ?0, 'action': ?1}")
    List<TaskHistory> findByTaskIdAndAction(Long taskId, String action);

    @Query(value = "{'taskId': ?0}", count = true)
    Long countByTaskId(Long taskId);
}
