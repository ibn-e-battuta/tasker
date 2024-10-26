package io.shinmen.app.tasker.interfaces;

import io.shinmen.app.tasker.model.Task;

public interface TaskNotificationOperations {
    void notifyWatchers(Task task, Long actorUserId, String action);
}
