package io.shinmen.app.tasker.dto;

import java.util.Set;

import io.shinmen.app.tasker.model.TeamPermission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TeamRoleCreateRequest {
    @NotBlank(message = "Role name is required")
    @Size(min = 2, max = 50, message = "Role name must be between 2 and 50 characters")
    private String name;

    private String description;

    @NotEmpty(message = "At least one permission is required")
    private Set<TeamPermission> permissions;
}
