package io.shinmen.app.tasker.dto;

import io.shinmen.app.tasker.model.Task;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.List;
import java.util.Map;


@Data
@Builder
public class TaskSearchResponse {
    private Long taskId;
    private String name;
    private String description;
    private Task.TaskStatus status;
    private Task.TaskPriority priority;
    private Long assignedUserId;
    private Set<String> labels;
    private LocalDateTime dueDate;
    private Double estimatedEffort;
    private Map<String, List<String>> highlights;
}
