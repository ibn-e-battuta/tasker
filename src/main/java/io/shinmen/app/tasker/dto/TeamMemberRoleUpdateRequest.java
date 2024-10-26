package io.shinmen.app.tasker.dto;

import java.util.Set;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class TeamMemberRoleUpdateRequest {
    @NotEmpty(message = "At least one role is required")
    private Set<String> roleNames;
}
