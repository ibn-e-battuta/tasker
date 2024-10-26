package io.shinmen.app.tasker.dto;

import java.util.Set;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TeamMemberResponse {
    private Long id;
    private UserResponse user;
    private Set<TeamRoleResponse> roles;
    private LocalDateTime joinedAt;
}
