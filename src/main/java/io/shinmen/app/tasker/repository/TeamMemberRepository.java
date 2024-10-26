package io.shinmen.app.tasker.repository;

import java.util.List;
import java.util.Optional;

import io.shinmen.app.tasker.model.TeamRole;
import org.springframework.data.jpa.repository.JpaRepository;

import io.shinmen.app.tasker.model.TeamMember;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {
    Optional<TeamMember> findByTeamIdAndUserId(Long teamId, Long userId);

    List<TeamMember> findByUserId(Long userId);

    List<TeamMember> findByTeamId(Long teamId);

    boolean existsByTeamIdAndUserId(Long teamId, Long userId);

    @Query("SELECT CASE WHEN COUNT(tm) > 0 THEN true ELSE false END FROM TeamMember tm JOIN tm.roles r WHERE r = :role")
    boolean existsByRolesContaining(@Param("role") TeamRole role);

}
