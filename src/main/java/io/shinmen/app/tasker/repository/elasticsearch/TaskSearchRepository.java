package io.shinmen.app.tasker.repository.elasticsearch;


import io.shinmen.app.tasker.model.Task;
import io.shinmen.app.tasker.model.TaskSearchDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface TaskSearchRepository extends ElasticsearchRepository<TaskSearchDocument, String> {

    @Query("{\"bool\": {\"must\": [{\"match\": {\"teamId\": \"?0\"}}, {\"multi_match\": {\"query\": \"?1\", \"fields\": [\"name^3\", \"description^2\", \"comments\"], \"type\": \"best_fields\", \"fuzziness\": \"AUTO\"}}]}}")
    Page<TaskSearchDocument> searchTeamTasks(Long teamId, String searchTerm, Pageable pageable);

    Page<TaskSearchDocument> findByTeamIdAndStatus(Long teamId, Task.TaskStatus status, Pageable pageable);

    Page<TaskSearchDocument> findByTeamIdAndPriority(Long teamId, Task.TaskPriority priority, Pageable pageable);

    Page<TaskSearchDocument> findByTeamIdAndAssignedUserId(Long teamId, Long userId, Pageable pageable);

    Page<TaskSearchDocument> findByTeamIdAndLabelsContaining(Long teamId, String label, Pageable pageable);

    void deleteByTaskId(Long taskId);
}
