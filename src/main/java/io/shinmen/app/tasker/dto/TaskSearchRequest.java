package io.shinmen.app.tasker.dto;

import io.shinmen.app.tasker.model.Task;
import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
public class TaskSearchRequest {
    private String searchTerm;
    private Task.TaskStatus status;
    private Task.TaskPriority priority;
    private Long assignedUserId;
    private Set<String> labels;
    private LocalDateTime dueDateStart;
    private LocalDateTime dueDateEnd;
}
