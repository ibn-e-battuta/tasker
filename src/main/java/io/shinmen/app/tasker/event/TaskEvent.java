package io.shinmen.app.tasker.event;

import java.util.Map;

import io.shinmen.app.tasker.model.Task;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskEvent {
    private EventType type;
    private Task task;
    private Long actorUserId;
    private Map<String, Object> data;

    public enum EventType {
        TASK_CREATED,
        TASK_UPDATED,
        TASK_DELETED,
        TASK_ASSIGNED,
        TASK_STATUS_CHANGED,
        TASK_LOCKED,
        TASK_UNLOCKED,
        COMMENT_ADDED,
        COMMENT_UPDATED,
        COMMENT_DELETED,
        ATTACHMENT_ADDED,
        ATTACHMENT_DELETED,
        WATCHER_ADDED,
        WATCHER_REMOVED,
        LABEL_ADDED,
        LABEL_REMOVED
    }
}
