package io.shinmen.app.tasker.dto;

import io.shinmen.app.tasker.model.Task;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TaskStatusTransitionRequest {
    @NotNull(message = "From status is required")
    private Task.TaskStatus fromStatus;

    @NotNull(message = "To status is required")
    private Task.TaskStatus toStatus;

    @NotNull(message = "Allowed flag is required")
    private boolean allowed;
}
