package io.shinmen.app.tasker.dto;

import io.shinmen.app.tasker.model.Task;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
public class TaskCreateRequest {
    @NotBlank(message = "Task name is required")
    @Size(min = 3, max = 200, message = "Task name must be between 3 and 200 characters")
    private String name;

    @Size(max = 5000, message = "Description cannot exceed 5000 characters")
    private String description;

    private Long assignedUserId;

    @NotNull(message = "Status is required")
    private Task.TaskStatus status;

    @NotNull(message = "Priority is required")
    private Task.TaskPriority priority;

    private LocalDateTime dueDate;

    private Double estimatedEffort;

    private Set<String> labels;

    private Set<Long> watcherIds;
}
