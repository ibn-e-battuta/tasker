package io.shinmen.app.tasker.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.shinmen.app.tasker.exception.CustomException;
import io.shinmen.app.tasker.model.Team;
import io.shinmen.app.tasker.model.TeamMember;
import io.shinmen.app.tasker.model.TeamPermission;
import io.shinmen.app.tasker.model.User;
import io.shinmen.app.tasker.repository.TeamMemberRepository;
import io.shinmen.app.tasker.repository.TeamRepository;
import io.shinmen.app.tasker.repository.UserRepository;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public void validatePermission(Long teamId, Long userId, TeamPermission permission) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException("Team not found", HttpStatus.NOT_FOUND));

        if (team.getOwner().getId().equals(userId)) {
            return;
        }

        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new CustomException("Not a team member", HttpStatus.FORBIDDEN));

        boolean hasPermission = member.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(perm -> perm.equals(permission));

        if (!hasPermission) {
            throw new CustomException(
                "Insufficient permissions: " + permission.name(),
                HttpStatus.FORBIDDEN
            );
        }
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(Long teamId, Long userId, TeamPermission permission) {
        try {
            validatePermission(teamId, userId, permission);
            return true;
        } catch (CustomException e) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public Set<TeamPermission> getUserPermissions(Long teamId, Long userId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException("Team not found", HttpStatus.NOT_FOUND));

        if (team.getOwner().getId().equals(userId)) {
            return Set.of(TeamPermission.values());
        }

        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new CustomException("Not a team member", HttpStatus.FORBIDDEN));

        return member.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public boolean isTeamOwner(Long teamId, Long userId) {
        return teamRepository.findById(teamId)
                .map(team -> team.getOwner().getId().equals(userId))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isTeamMember(Long teamId, Long userId) {
        return teamMemberRepository.existsByTeamIdAndUserId(teamId, userId);
    }

    public boolean validateTeamOwnerOrAdmin(Long teamId, Long userId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException("Team not found", HttpStatus.NOT_FOUND));

        if (team.getOwner().getId().equals(userId)) {
            return true;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        return user.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_ADMIN"));
    }
}
