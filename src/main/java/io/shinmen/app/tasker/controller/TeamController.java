package io.shinmen.app.tasker.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDateTime;

import io.shinmen.app.tasker.dto.ApiResponse;
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
import io.shinmen.app.tasker.security.UserPrincipal;
import io.shinmen.app.tasker.service.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TeamResponse> createTeam(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody TeamCreateRequest request) {
        TeamResponse team = teamService.createTeam(currentUser.getId(), request);
        return ResponseEntity.ok(team);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TeamResponse>> getUserTeams(@AuthenticationPrincipal UserPrincipal currentUser) {
        List<TeamResponse> teams = teamService.getUserTeams(currentUser.getId());
        return ResponseEntity.ok(teams);
    }

    @GetMapping("/{teamId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TeamResponse> getTeam(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long teamId) {
        TeamResponse team = teamService.getTeamById(teamId, currentUser.getId());
        return ResponseEntity.ok(team);
    }

    @PutMapping("/{teamId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TeamResponse> updateTeam(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long teamId,
            @Valid @RequestBody TeamUpdateRequest request) {
        TeamResponse team = teamService.updateTeam(teamId, currentUser.getId(), request);
        return ResponseEntity.ok(team);
    }

    @GetMapping("/{teamId}/members")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TeamMemberResponse>> getTeamMembers(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long teamId) {
        List<TeamMemberResponse> members = teamService.getTeamMembers(teamId, currentUser.getId());
        return ResponseEntity.ok(members);
    }

    @PostMapping("/{teamId}/invitations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TeamInvitationResponse> inviteUser(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long teamId,
            @Valid @RequestBody TeamInvitationRequest request) {
        TeamInvitationResponse invitation = teamService.inviteUser(teamId, currentUser.getId(), request);
        return ResponseEntity.ok(invitation);
    }

    @PostMapping("/invitations/{token}/accept")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TeamResponse> acceptInvitation(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable String token) {
        TeamResponse team = teamService.acceptInvitation(token, currentUser.getId());
        return ResponseEntity.ok(team);
    }

    @PostMapping("/invitations/{token}/decline")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> declineInvitation(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable String token) {
        teamService.declineInvitation(token, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Invitation declined successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PutMapping("/{teamId}/members/{memberId}/roles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> updateMemberRoles(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long teamId,
            @PathVariable Long memberId,
            @Valid @RequestBody TeamMemberRoleUpdateRequest request) {
        teamService.updateMemberRoles(teamId, currentUser.getId(), memberId, request);
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Member roles updated successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }

    @DeleteMapping("/{teamId}/members/{memberId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> removeMember(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long teamId,
            @PathVariable Long memberId) {
        teamService.removeMember(teamId, currentUser.getId(), memberId);
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Member removed successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PostMapping("/{teamId}/leave")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> leaveTeam(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long teamId) {
        teamService.leaveTeam(teamId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Left team successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PostMapping("/{teamId}/transfer-ownership/{newOwnerId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> transferOwnership(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long teamId,
            @PathVariable Long newOwnerId) {
        teamService.transferOwnership(teamId, currentUser.getId(), newOwnerId);
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Team ownership transferred successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }

    @GetMapping("/{teamId}/roles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TeamRoleResponse>> getTeamRoles(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long teamId) {
        List<TeamRoleResponse> roles = teamService.getTeamRoles(teamId, currentUser.getId());
        return ResponseEntity.ok(roles);
    }

    @PostMapping("/{teamId}/roles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TeamRoleResponse> createTeamRole(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long teamId,
            @Valid @RequestBody TeamRoleCreateRequest request) {
        TeamRoleResponse role = teamService.createTeamRole(teamId, currentUser.getId(), request);
        return ResponseEntity.ok(role);
    }

    @PutMapping("/{teamId}/roles/{roleId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TeamRoleResponse> updateTeamRole(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long teamId,
            @PathVariable Long roleId,
            @Valid @RequestBody TeamRoleUpdateRequest request) {
        TeamRoleResponse role = teamService.updateTeamRole(teamId, roleId, currentUser.getId(), request);
        return ResponseEntity.ok(role);
    }

    @DeleteMapping("/{teamId}/roles/{roleId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> deleteTeamRole(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long teamId,
            @PathVariable Long roleId) {
        teamService.deleteTeamRole(teamId, roleId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Team role deleted successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }
}
