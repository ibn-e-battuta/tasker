package io.shinmen.app.tasker.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

import io.shinmen.app.tasker.model.TeamRole;

public interface TeamRoleRepository extends JpaRepository<TeamRole, Long> {
    List<TeamRole> findByTeamId(Long teamId);

    Optional<TeamRole> findByTeamIdAndName(Long teamId, String name);

    boolean existsByTeamIdAndName(Long teamId, String name);
}
