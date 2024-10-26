package io.shinmen.app.tasker.interfaces;

import io.shinmen.app.tasker.model.Task;

public interface TaskWorkflowOperations {
    boolean isFinalStatus(Long teamId, Task.TaskStatus status);
    void validateStatusTransition(Long teamId, Task.TaskStatus fromStatus, Task.TaskStatus toStatus);
    void revertTaskStatus(Long taskId, Long userId, Task.TaskStatus newStatus);
}
