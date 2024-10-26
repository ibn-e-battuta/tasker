package io.shinmen.app.tasker.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.EnumSet;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.shinmen.app.tasker.dto.TeamCreateRequest;
import io.shinmen.app.tasker.dto.TeamInvitationRequest;
import io.shinmen.app.tasker.dto.TeamInvitationResponse;
import io.shinmen.app.tasker.dto.TeamMemberResponse;
import io.shinmen.app.tasker.dto.TeamMemberRoleUpdateRequest;
import io.shinmen.app.tasker.dto.TeamResponse;
import io.shinmen.app.tasker.dto.TeamRoleCreateRequest;
import io.shinmen.app.tasker.dto.TeamRoleResponse;
import io.shinmen.app.tasker.dto.TeamRoleUpdateRequest;
import io.shinmen.app.tasker.dto.TeamUpdateRequest;
import io.shinmen.app.tasker.dto.UserResponse;
import io.shinmen.app.tasker.exception.CustomException;
import io.shinmen.app.tasker.model.Team;
import io.shinmen.app.tasker.model.TeamInvitation;
import io.shinmen.app.tasker.model.TeamMember;
import io.shinmen.app.tasker.model.TeamPermission;
import io.shinmen.app.tasker.model.TeamRole;
import io.shinmen.app.tasker.model.User;
import io.shinmen.app.tasker.repository.TeamInvitationRepository;
import io.shinmen.app.tasker.repository.TeamMemberRepository;
import io.shinmen.app.tasker.repository.TeamRepository;
import io.shinmen.app.tasker.repository.TeamRoleRepository;
import io.shinmen.app.tasker.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamRoleRepository teamRoleRepository;
    private final TeamInvitationRepository teamInvitationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AuditService auditService;

    @Transactional
    public TeamResponse createTeam(Long userId, TeamCreateRequest request) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        if (teamRepository.existsByNameAndOwnerId(request.getName(), userId)) {
            throw new CustomException("You already have a team with this name", HttpStatus.BAD_REQUEST);
        }

        Team team = Team.builder()
                .name(request.getName())
                .description(request.getDescription())
                .owner(owner)
                .build();

        team = teamRepository.save(team);

        createDefaultTeamRoles(team);

        addOwnerAsMember(team);

        auditService.auditUserAction(userId, "TEAM_CREATED",
                String.format("Created team: %s", team.getName()));

        return mapTeamToResponse(team);
    }

    private void createDefaultTeamRoles(Team team) {
        Set<TeamPermission> adminPermissions = new HashSet<>(Arrays.asList(TeamPermission.values()));
        TeamRole adminRole = TeamRole.builder()
                .team(team)
                .name("Admin")
                .description("Full access to all team features")
                .permissions(adminPermissions)
                .build();

        Set<TeamPermission> memberPermissions = Set.of(
                TeamPermission.VIEW_TASK,
                TeamPermission.VIEW_COMMENTS,
                TeamPermission.VIEW_ATTACHMENTS,
                TeamPermission.VIEW_MEMBERS,
                TeamPermission.VIEW_TEAM_SETTINGS);
        TeamRole memberRole = TeamRole.builder()
                .team(team)
                .name("Member")
                .description("Basic team member access")
                .permissions(memberPermissions)
                .build();

        teamRoleRepository.saveAll(Arrays.asList(adminRole, memberRole));
    }

    private void addOwnerAsMember(Team team) {
        TeamRole adminRole = teamRoleRepository.findByTeamIdAndName(team.getId(), "Admin")
                .orElseThrow(() -> new CustomException("Admin role not found", HttpStatus.INTERNAL_SERVER_ERROR));

        TeamMember ownerMember = TeamMember.builder()
                .team(team)
                .user(team.getOwner())
                .roles(Set.of(adminRole))
                .build();

        teamMemberRepository.save(ownerMember);
    }

    @Transactional
    public TeamInvitationResponse inviteUser(Long teamId, Long inviterId, TeamInvitationRequest request) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException("Team not found", HttpStatus.NOT_FOUND));

        validateTeamPermission(team.getId(), inviterId, TeamPermission.MANAGE_MEMBERS);

        int pendingCount = teamInvitationRepository.countByTeamIdAndStatus(
                teamId,
                TeamInvitation.InvitationStatus.PENDING);
        if (pendingCount >= team.getMaxPendingInvitations()) {
            throw new CustomException("Maximum pending invitations limit reached", HttpStatus.BAD_REQUEST);
        }

        User existingUser = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (existingUser != null && teamMemberRepository.existsByTeamIdAndUserId(teamId, existingUser.getId())) {
            throw new CustomException("User is already a team member", HttpStatus.BAD_REQUEST);
        }

        if (teamInvitationRepository.existsByTeamIdAndEmailAndStatus(
                teamId,
                request.getEmail(),
                TeamInvitation.InvitationStatus.PENDING)) {
            throw new CustomException("Invitation already sent to this email", HttpStatus.BAD_REQUEST);
        }

        Set<TeamRole> roles = validateAndGetRoles(teamId, request.getRoleNames());

        TeamInvitation invitation = TeamInvitation.builder()
                .team(team)
                .email(request.getEmail())
                .token(UUID.randomUUID().toString())
                .expiryDate(LocalDateTime.now().plusDays(7))
                .status(TeamInvitation.InvitationStatus.PENDING)
                .build();

        invitation = teamInvitationRepository.save(invitation);

        emailService.sendTeamInvitation(invitation, roles);

        auditService.auditUserAction(inviterId, "TEAM_INVITATION_SENT",
                String.format("Sent invitation to %s for team: %s", request.getEmail(), team.getName()));

        return mapInvitationToResponse(invitation);
    }

    @Transactional
    public TeamResponse acceptInvitation(String token, Long userId) {
        TeamInvitation invitation = teamInvitationRepository.findByToken(token)
                .orElseThrow(() -> new CustomException("Invalid invitation token", HttpStatus.BAD_REQUEST));

        validateInvitation(invitation);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        if (!user.getEmail().equals(invitation.getEmail())) {
            throw new CustomException("Invitation was sent to a different email address", HttpStatus.BAD_REQUEST);
        }

        TeamRole memberRole = teamRoleRepository.findByTeamIdAndName(invitation.getTeam().getId(), "Member")
                .orElseThrow(() -> new CustomException("Member role not found", HttpStatus.INTERNAL_SERVER_ERROR));

        TeamMember member = TeamMember.builder()
                .team(invitation.getTeam())
                .user(user)
                .roles(Set.of(memberRole))
                .build();

        teamMemberRepository.save(member);

        invitation.setStatus(TeamInvitation.InvitationStatus.ACCEPTED);
        teamInvitationRepository.save(invitation);

        auditService.auditUserAction(userId, "TEAM_INVITATION_ACCEPTED",
                String.format("Joined team: %s", invitation.getTeam().getName()));

        return mapTeamToResponse(invitation.getTeam());
    }

    @Transactional
    public void declineInvitation(String token, Long userId) {
        TeamInvitation invitation = teamInvitationRepository.findByToken(token)
                .orElseThrow(() -> new CustomException("Invalid invitation token", HttpStatus.BAD_REQUEST));

        validateInvitation(invitation);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        if (!user.getEmail().equals(invitation.getEmail())) {
            throw new CustomException("Invitation was sent to a different email address", HttpStatus.BAD_REQUEST);
        }

        invitation.setStatus(TeamInvitation.InvitationStatus.DECLINED);
        teamInvitationRepository.save(invitation);

        auditService.auditUserAction(userId, "TEAM_INVITATION_DECLINED",
                String.format("Declined invitation to team: %s", invitation.getTeam().getName()));
    }

    @Transactional
    public void updateMemberRoles(Long teamId, Long updaterId, Long memberId, TeamMemberRoleUpdateRequest request) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException("Team not found", HttpStatus.NOT_FOUND));

        validateTeamPermission(teamId, updaterId, TeamPermission.MANAGE_MEMBER_ROLES);

        if (team.getOwner().getId().equals(memberId)) {
            throw new CustomException("Cannot modify team owner's roles", HttpStatus.BAD_REQUEST);
        }

        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(teamId, memberId)
                .orElseThrow(() -> new CustomException("Team member not found", HttpStatus.NOT_FOUND));

        Set<TeamRole> newRoles = validateAndGetRoles(teamId, request.getRoleNames());
        member.setRoles(newRoles);
        teamMemberRepository.save(member);

        auditService.auditUserAction(updaterId, "TEAM_MEMBER_ROLES_UPDATED",
                String.format("Updated roles for member %s in team: %s", memberId, team.getName()));
    }

    @Transactional
    public void removeMember(Long teamId, Long removerId, Long memberId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException("Team not found", HttpStatus.NOT_FOUND));

        validateTeamPermission(teamId, removerId, TeamPermission.MANAGE_MEMBERS);

        if (team.getOwner().getId().equals(memberId)) {
            throw new CustomException("Cannot remove team owner", HttpStatus.BAD_REQUEST);
        }

        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(teamId, memberId)
                .orElseThrow(() -> new CustomException("Team member not found", HttpStatus.NOT_FOUND));

        reassignMemberTasks(member, team.getOwner());

        teamMemberRepository.delete(member);

        auditService.auditUserAction(removerId, "TEAM_MEMBER_REMOVED",
                String.format("Removed member %s from team: %s", memberId, team.getName()));
    }

    @Transactional
    public void leaveTeam(Long teamId, Long userId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException("Team not found", HttpStatus.NOT_FOUND));

        if (team.getOwner().getId().equals(userId)) {
            throw new CustomException(
                    "Team owner cannot leave. Transfer ownership first.",
                    HttpStatus.BAD_REQUEST);
        }

        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new CustomException("Team member not found", HttpStatus.NOT_FOUND));

        reassignMemberTasks(member, team.getOwner());

        teamMemberRepository.delete(member);

        auditService.auditUserAction(userId, "TEAM_LEFT",
                String.format("Left team: %s", team.getName()));
    }

    @Transactional
    public void transferOwnership(Long teamId, Long currentOwnerId, Long newOwnerId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException("Team not found", HttpStatus.NOT_FOUND));

        if (!team.getOwner().getId().equals(currentOwnerId)) {
            throw new CustomException("Only team owner can transfer ownership", HttpStatus.FORBIDDEN);
        }

        TeamMember newOwnerMember = teamMemberRepository.findByTeamIdAndUserId(teamId, newOwnerId)
                .orElseThrow(() -> new CustomException("New owner must be a team member", HttpStatus.BAD_REQUEST));

        User newOwner = newOwnerMember.getUser();
        User currentOwner = team.getOwner();

        team.setOwner(newOwner);
        teamRepository.save(team);

        auditService.auditUserAction(currentOwnerId, "TEAM_OWNERSHIP_TRANSFERRED",
                String.format("Transferred ownership of team %s to user %s", team.getName(), newOwnerId));
    }

    private void validateTeamPermission(Long teamId, Long userId, TeamPermission permission) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException("Team not found", HttpStatus.NOT_FOUND));

        if (team.getOwner().getId().equals(userId)) {
            return;
        }

        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new CustomException("Not a team member", HttpStatus.FORBIDDEN));

        boolean hasPermission = member.getRoles().stream()
                .anyMatch(role -> role.getPermissions().contains(permission));

        if (!hasPermission) {
            throw new CustomException("Insufficient permissions", HttpStatus.FORBIDDEN);
        }
    }

    private void validateInvitation(TeamInvitation invitation) {
        if (invitation.getStatus() != TeamInvitation.InvitationStatus.PENDING) {
            throw new CustomException("Invitation is no longer valid", HttpStatus.BAD_REQUEST);
        }

        if (invitation.getExpiryDate().isBefore(LocalDateTime.now())) {
            invitation.setStatus(TeamInvitation.InvitationStatus.EXPIRED);
            teamInvitationRepository.save(invitation);
            throw new CustomException("Invitation has expired", HttpStatus.BAD_REQUEST);
        }
    }

    private Set<TeamRole> validateAndGetRoles(Long teamId, Set<String> roleNames) {
        return roleNames.stream()
                .map(roleName -> teamRoleRepository.findByTeamIdAndName(teamId, roleName)
                        .orElseThrow(() -> new CustomException(
                                "Role not found: " + roleName,
                                HttpStatus.BAD_REQUEST)))
                .collect(Collectors.toSet());
    }

    private void reassignMemberTasks(TeamMember member, User newAssignee) {
        auditService.auditUserAction(
                newAssignee.getId(),
                "TASKS_REASSIGNED",
                String.format("Tasks reassigned from user %s to user %s in team %s",
                        member.getUser().getId(),
                        newAssignee.getId(),
                        member.getTeam().getName()));
    }

    private TeamResponse mapTeamToResponse(Team team) {
        return TeamResponse.builder()
                .id(team.getId())
                .name(team.getName())
                .description(team.getDescription())
                .owner(mapUserToResponse(team.getOwner()))
                .memberCount(team.getMembers().size())
                .createdAt(team.getCreatedAt())
                .updatedAt(team.getUpdatedAt())
                .build();
    }

    private TeamMemberResponse mapTeamMemberToResponse(TeamMember member) {
        return TeamMemberResponse.builder()
                .id(member.getId())
                .user(mapUserToResponse(member.getUser()))
                .roles(member.getRoles().stream()
                        .map(this::mapTeamRoleToResponse)
                        .collect(Collectors.toSet()))
                .joinedAt(member.getCreatedAt())
                .build();
    }

    private TeamRoleResponse mapTeamRoleToResponse(TeamRole role) {
        return TeamRoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .permissions(role.getPermissions())
                .build();
    }

    private TeamInvitationResponse mapInvitationToResponse(TeamInvitation invitation) {
        return TeamInvitationResponse.builder()
                .id(invitation.getId())
                .email(invitation.getEmail())
                .team(mapTeamToResponse(invitation.getTeam()))
                .status(invitation.getStatus())
                .expiryDate(invitation.getExpiryDate())
                .createdAt(invitation.getCreatedAt())
                .build();
    }

    private UserResponse mapUserToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .build();
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> getUserTeams(Long userId) {
        List<Team> ownedTeams = teamRepository.findByOwnerId(userId);
        List<Team> memberTeams = teamRepository.findTeamsByMemberId(userId);

        return Stream.concat(ownedTeams.stream(), memberTeams.stream())
                .distinct()
                .map(this::mapTeamToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TeamResponse getTeamById(Long teamId, Long userId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException("Team not found", HttpStatus.NOT_FOUND));

        if (!team.getOwner().getId().equals(userId) &&
                !teamMemberRepository.existsByTeamIdAndUserId(teamId, userId)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }

        return mapTeamToResponse(team);
    }

    @Transactional(readOnly = true)
    public List<TeamMemberResponse> getTeamMembers(Long teamId, Long userId) {
        if (!hasTeamAccess(teamId, userId)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }

        List<TeamMember> members = teamMemberRepository.findByTeamId(teamId);
        return members.stream()
                .map(this::mapTeamMemberToResponse)
                .collect(Collectors.toList());
    }

    private boolean hasTeamAccess(Long teamId, Long userId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException("Team not found", HttpStatus.NOT_FOUND));

        return team.getOwner().getId().equals(userId) ||
                teamMemberRepository.existsByTeamIdAndUserId(teamId, userId);
    }

    @Transactional
    public TeamRoleResponse createTeamRole(Long teamId, Long userId, TeamRoleCreateRequest request) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException("Team not found", HttpStatus.NOT_FOUND));

        if (!team.getOwner().getId().equals(userId)) {
            throw new CustomException("Only team owner can create roles", HttpStatus.FORBIDDEN);
        }

        if (teamRoleRepository.existsByTeamIdAndName(teamId, request.getName())) {
            throw new CustomException("Role name already exists", HttpStatus.BAD_REQUEST);
        }

        validatePermissions(request.getPermissions());

        TeamRole role = TeamRole.builder()
                .team(team)
                .name(request.getName())
                .description(request.getDescription())
                .permissions(request.getPermissions())
                .build();

        role = teamRoleRepository.save(role);

        auditService.auditUserAction(userId, "TEAM_ROLE_CREATED",
                String.format("Created role %s in team %s", role.getName(), team.getName()));

        return mapTeamRoleToResponse(role);
    }

    @Transactional
    public TeamRoleResponse updateTeamRole(Long teamId, Long roleId, Long userId, TeamRoleUpdateRequest request) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException("Team not found", HttpStatus.NOT_FOUND));

        if (!team.getOwner().getId().equals(userId)) {
            throw new CustomException("Only team owner can update roles", HttpStatus.FORBIDDEN);
        }

        TeamRole role = teamRoleRepository.findById(roleId)
                .orElseThrow(() -> new CustomException("Role not found", HttpStatus.NOT_FOUND));

        if (!role.getTeam().getId().equals(teamId)) {
            throw new CustomException("Role does not belong to team", HttpStatus.BAD_REQUEST);
        }

        if (isDefaultRole(role.getName())) {
            throw new CustomException("Cannot modify default roles", HttpStatus.BAD_REQUEST);
        }

        if (!role.getName().equals(request.getName()) &&
            teamRoleRepository.existsByTeamIdAndName(teamId, request.getName())) {
            throw new CustomException("Role name already exists", HttpStatus.BAD_REQUEST);
        }

        validatePermissions(request.getPermissions());

        role.setName(request.getName());
        role.setDescription(request.getDescription());
        role.setPermissions(request.getPermissions());

        role = teamRoleRepository.save(role);

        auditService.auditUserAction(userId, "TEAM_ROLE_UPDATED",
                String.format("Updated role %s in team %s", role.getName(), team.getName()));

        return mapTeamRoleToResponse(role);
    }

    @Transactional
    public void deleteTeamRole(Long teamId, Long roleId, Long userId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException("Team not found", HttpStatus.NOT_FOUND));

        if (!team.getOwner().getId().equals(userId)) {
            throw new CustomException("Only team owner can delete roles", HttpStatus.FORBIDDEN);
        }

        TeamRole role = teamRoleRepository.findById(roleId)
                .orElseThrow(() -> new CustomException("Role not found", HttpStatus.NOT_FOUND));

        if (!role.getTeam().getId().equals(teamId)) {
            throw new CustomException("Role does not belong to team", HttpStatus.BAD_REQUEST);
        }

        if (isDefaultRole(role.getName())) {
            throw new CustomException("Cannot delete default roles", HttpStatus.BAD_REQUEST);
        }

        if (isRoleInUse(role)) {
            throw new CustomException("Cannot delete role that is assigned to members", HttpStatus.BAD_REQUEST);
        }

        teamRoleRepository.delete(role);

        auditService.auditUserAction(userId, "TEAM_ROLE_DELETED",
                String.format("Deleted role %s from team %s", role.getName(), team.getName()));
    }

    @Transactional(readOnly = true)
    public List<TeamRoleResponse> getTeamRoles(Long teamId, Long userId) {
        if (!hasTeamAccess(teamId, userId)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }

        List<TeamRole> roles = teamRoleRepository.findByTeamId(teamId);
        return roles.stream()
                .map(this::mapTeamRoleToResponse)
                .collect(Collectors.toList());
    }

    private boolean isDefaultRole(String roleName) {
        return "Admin".equals(roleName) || "Member".equals(roleName);
    }

    private boolean isRoleInUse(TeamRole role) {
        return teamMemberRepository.existsByRolesContaining(role);
    }

    private void validatePermissions(Set<TeamPermission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            throw new CustomException("At least one permission is required", HttpStatus.BAD_REQUEST);
        }

        for (TeamPermission permission : permissions) {
            if (!EnumSet.allOf(TeamPermission.class).contains(permission)) {
                throw new CustomException("Invalid permission: " + permission, HttpStatus.BAD_REQUEST);
            }
        }
    }

    @Transactional
    public TeamResponse updateTeam(Long teamId, Long userId, TeamUpdateRequest request) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException("Team not found", HttpStatus.NOT_FOUND));

        if (!team.getOwner().getId().equals(userId)) {
            throw new CustomException("Only team owner can update team", HttpStatus.FORBIDDEN);
        }

        if (!team.getName().equals(request.getName()) &&
            teamRepository.existsByNameAndOwnerId(request.getName(), userId)) {
            throw new CustomException("You already have a team with this name", HttpStatus.BAD_REQUEST);
        }

        team.setName(request.getName());
        team.setDescription(request.getDescription());
        team = teamRepository.save(team);

        auditService.auditUserAction(userId, "TEAM_UPDATED",
                String.format("Updated team: %s", team.getName()));

        return mapTeamToResponse(team);
    }
}
