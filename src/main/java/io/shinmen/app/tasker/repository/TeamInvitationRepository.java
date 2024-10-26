package io.shinmen.app.tasker.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.shinmen.app.tasker.model.TeamInvitation;
import io.shinmen.app.tasker.model.TeamInvitation.InvitationStatus;

public interface TeamInvitationRepository extends JpaRepository<TeamInvitation, Long> {
    Optional<TeamInvitation> findByToken(String token);

    List<TeamInvitation> findByTeamIdAndStatus(Long teamId, InvitationStatus status);

    List<TeamInvitation> findByEmailAndStatus(String email, InvitationStatus status);

    int countByTeamIdAndStatus(Long teamId, InvitationStatus status);

    boolean existsByTeamIdAndEmailAndStatus(Long teamId, String email, InvitationStatus status);
}
