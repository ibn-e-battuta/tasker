package io.shinmen.app.tasker.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.shinmen.app.tasker.model.Task;
import io.shinmen.app.tasker.model.TeamWorkflowTransition;

public interface TeamWorkflowTransitionRepository extends JpaRepository<TeamWorkflowTransition, Long> {
    List<TeamWorkflowTransition> findByWorkflowConfigId(Long workflowConfigId);
    Optional<TeamWorkflowTransition> findByWorkflowConfigIdAndFromStatusAndToStatus(
        Long workflowConfigId,
        Task.TaskStatus fromStatus,
        Task.TaskStatus toStatus
    );
}
