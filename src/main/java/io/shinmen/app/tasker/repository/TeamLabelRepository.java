package io.shinmen.app.tasker.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.shinmen.app.tasker.model.TeamLabel;

public interface TeamLabelRepository extends JpaRepository<TeamLabel, Long> {
    List<TeamLabel> findByTeamId(Long teamId);
    boolean existsByTeamIdAndNameIgnoreCase(Long teamId, String name);
    Optional<TeamLabel> findByTeamIdAndNameIgnoreCase(Long teamId, String name);
}
