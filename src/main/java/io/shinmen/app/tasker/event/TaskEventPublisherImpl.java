package io.shinmen.app.tasker.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import io.shinmen.app.tasker.model.Task;
import io.shinmen.app.tasker.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class TaskEventPublisherImpl implements TaskEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void publishTaskCreated(Task task, Long actorUserId) {
        TaskEvent event = TaskEvent.builder()
                .type(TaskEvent.EventType.TASK_CREATED)
                .task(task)
                .actorUserId(actorUserId)
                .build();

        eventPublisher.publishEvent(event);
    }

    @Override
    public void publishTaskUpdated(Task task, Long actorUserId, String action,
            Object oldValue, Object newValue) {
        TaskEvent event = TaskEvent.builder()
                .type(TaskEvent.EventType.TASK_UPDATED)
                .task(task)
                .actorUserId(actorUserId)
                .data(Map.of(
                    "action", action,
                    "oldValue", oldValue,
                    "newValue", newValue
                ))
                .build();

        eventPublisher.publishEvent(event);
    }

    @Override
    public void publishTaskDeleted(Task task, Long actorUserId) {
        TaskEvent event = TaskEvent.builder()
                .type(TaskEvent.EventType.TASK_DELETED)
                .task(task)
                .actorUserId(actorUserId)
                .build();

        eventPublisher.publishEvent(event);
    }

    @Override
    public void publishTaskAssigned(Task task, User oldAssignee, User newAssignee, Long actorUserId) {
        TaskEvent event = TaskEvent.builder()
                .type(TaskEvent.EventType.TASK_ASSIGNED)
                .task(task)
                .actorUserId(actorUserId)
                .data(Map.of(
                    "oldAssignee", oldAssignee,
                    "newAssignee", newAssignee
                ))
                .build();

        eventPublisher.publishEvent(event);
    }

    @Override
    public void publishTaskStatusChanged(Task task, Long actorUserId, Object oldStatus,
            Object newStatus) {
        TaskEvent event = TaskEvent.builder()
                .type(TaskEvent.EventType.TASK_STATUS_CHANGED)
                .task(task)
                .actorUserId(actorUserId)
                .data(Map.of(
                    "oldStatus", oldStatus,
                    "newStatus", newStatus
                ))
                .build();

        eventPublisher.publishEvent(event);
    }

    @Override
    public void publishCommentAdded(Task task, Long commentId, Long actorUserId) {
        TaskEvent event = TaskEvent.builder()
                .type(TaskEvent.EventType.COMMENT_ADDED)
                .task(task)
                .actorUserId(actorUserId)
                .data(Map.of("commentId", commentId))
                .build();

        eventPublisher.publishEvent(event);
    }

    @Override
    public void publishTaskLocked(Task task, Long actorUserId) {
        TaskEvent event = TaskEvent.builder()
                .type(TaskEvent.EventType.TASK_LOCKED)
                .task(task)
                .actorUserId(actorUserId)
                .build();

        eventPublisher.publishEvent(event);
    }

    @Override
    public void publishTaskUnlocked(Task task, Long actorUserId) {
        TaskEvent event = TaskEvent.builder()
                .type(TaskEvent.EventType.TASK_UNLOCKED)
                .task(task)
                .actorUserId(actorUserId)
                .build();

        eventPublisher.publishEvent(event);
    }

    @Override
    public void publishWatcherAdded(Task task, User watcher, Long actorUserId) {
        TaskEvent event = TaskEvent.builder()
                .type(TaskEvent.EventType.WATCHER_ADDED)
                .task(task)
                .actorUserId(actorUserId)
                .data(Map.of("watcher", watcher))
                .build();

        eventPublisher.publishEvent(event);
    }

    @Override
    public void publishWatcherRemoved(Task task, User watcher, Long actorUserId) {
        TaskEvent event = TaskEvent.builder()
                .type(TaskEvent.EventType.WATCHER_REMOVED)
                .task(task)
                .actorUserId(actorUserId)
                .data(Map.of("watcher", watcher))
                .build();

        eventPublisher.publishEvent(event);
    }

    @Override
    public void publish(TaskEvent event) {
        try {
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.error("Error publishing task event: {}", event, e);
        }
    }
}
