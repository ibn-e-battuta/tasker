package io.shinmen.app.tasker.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.shinmen.app.tasker.model.TeamWorkflowConfig;

import java.util.Optional;

public interface TeamWorkflowConfigRepository extends JpaRepository<TeamWorkflowConfig, Long> {
    Optional<TeamWorkflowConfig> findByTeamId(Long teamId);
}
