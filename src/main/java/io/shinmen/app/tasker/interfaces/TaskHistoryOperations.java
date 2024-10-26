package io.shinmen.app.tasker.interfaces;

import java.util.Map;

import io.shinmen.app.tasker.model.Task;

public interface TaskHistoryOperations {
    void recordTaskHistory(Task task, Long userId, String action, Object oldValue, Object newValue, Map<String, Object> additionalInfo);
    void recordTaskChanges(Task task, Long userId, Map<String, Object> oldValues, Map<String, Object> newValues);
}
