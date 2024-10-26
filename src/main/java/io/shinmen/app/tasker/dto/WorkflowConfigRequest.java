package io.shinmen.app.tasker.dto;

import io.shinmen.app.tasker.model.Task;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Set;

@Data
public class WorkflowConfigRequest {
    @NotEmpty(message = "Final statuses must not be empty")
    private Set<Task.TaskStatus> finalStatuses;

    private boolean allowStatusReversion;

    private Set<Long> statusReversionRoleIds;
}
