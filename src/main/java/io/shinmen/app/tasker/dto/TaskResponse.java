package io.shinmen.app.tasker.dto;

import io.shinmen.app.tasker.model.Task;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
public class TaskResponse {
    private Long id;
    private Long teamId;
    private String name;
    private String description;
    private UserResponse assignedUser;
    private Task.TaskStatus status;
    private Task.TaskPriority priority;
    private LocalDateTime dueDate;
    private Double estimatedEffort;
    private Set<String> labels;
    private Set<UserResponse> watchers;
    private boolean locked;
    private boolean finalStatus;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
