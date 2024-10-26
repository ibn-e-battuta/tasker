package io.shinmen.app.tasker.dto;

import io.shinmen.app.tasker.model.Task;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
public class TaskFilter {
    private Set<Task.TaskStatus> statuses;
    private Set<Task.TaskPriority> priorities;
    private Set<Long> assignedUserIds;
    private Set<String> labels;
    private LocalDateTime dueDateStart;
    private LocalDateTime dueDateEnd;
    private Double minEstimatedEffort;
    private Double maxEstimatedEffort;
    private Boolean isLocked;
    private Boolean isFinalStatus;
    private LocalDateTime createdAfter;
    private LocalDateTime createdBefore;
    private LocalDateTime updatedAfter;
    private LocalDateTime updatedBefore;
    private String searchTerm;
}
