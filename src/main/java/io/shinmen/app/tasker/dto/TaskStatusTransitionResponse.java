package io.shinmen.app.tasker.dto;

import io.shinmen.app.tasker.model.Task;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskStatusTransitionResponse {
    private Long id;
    private Task.TaskStatus fromStatus;
    private Task.TaskStatus toStatus;
    private boolean allowed;
}
