package io.shinmen.app.tasker.dto;

import java.util.Set;

import io.shinmen.app.tasker.model.TeamPermission;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TeamRoleResponse {
    private Long id;
    private String name;
    private String description;
    private Set<TeamPermission> permissions;
}
