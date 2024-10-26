package io.shinmen.app.tasker.dto;

import java.util.Map;

import io.shinmen.app.tasker.model.Task.TaskPriority;
import io.shinmen.app.tasker.model.Task.TaskStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskStatistics {
    private Long totalTasks;
    private Map<TaskStatus, Long> tasksByStatus;
    private Map<TaskPriority, Long> tasksByPriority;
    private Map<String, Long> tasksByLabel;
    private Map<Long, Long> tasksByAssignee;
    private double averageCompletionTime;
    private Map<String, Double> completionTimeByPriority;
    private int overdueTasks;
    private Map<String, Integer> overdueTasksByPriority;
}
