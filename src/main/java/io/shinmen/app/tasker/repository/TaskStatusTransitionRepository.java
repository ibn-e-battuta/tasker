package io.shinmen.app.tasker.repository;

import io.shinmen.app.tasker.model.Task;
import io.shinmen.app.tasker.model.TaskStatusTransition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskStatusTransitionRepository extends JpaRepository<TaskStatusTransition, Long> {
    List<TaskStatusTransition> findByTeamId(Long teamId);

    Optional<TaskStatusTransition> findByTeamIdAndFromStatusAndToStatus(
            Long teamId,
            Task.TaskStatus fromStatus,
            Task.TaskStatus toStatus
    );
}
