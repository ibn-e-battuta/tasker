package io.shinmen.app.tasker.dto;


import io.shinmen.app.tasker.model.Task;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StatusReversionRequest {
    @NotNull(message = "New status is required")
    private Task.TaskStatus newStatus;
}
