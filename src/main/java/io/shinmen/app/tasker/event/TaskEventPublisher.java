package io.shinmen.app.tasker.event;

import io.shinmen.app.tasker.model.Task;
import io.shinmen.app.tasker.model.User;

public interface TaskEventPublisher {
    public void publish(TaskEvent event);
    void publishTaskCreated(Task task, Long actorUserId);
    void publishTaskDeleted(Task task, Long actorUserId);
    void publishTaskAssigned(Task task, User oldAssignee, User newAssignee, Long actorUserId);
    void publishTaskStatusChanged(Task task, Long actorUserId, Object oldStatus, Object newStatus);
    void publishCommentAdded(Task task, Long commentId, Long actorUserId);
    void publishTaskLocked(Task task, Long actorUserId);
    void publishTaskUnlocked(Task task, Long actorUserId);
    void publishWatcherAdded(Task task, User watcher, Long actorUserId);
    void publishWatcherRemoved(Task task, User watcher, Long actorUserId);
    void publishTaskUpdated(Task task, Long actorUserId, String action, Object oldValue, Object newValue);
}
