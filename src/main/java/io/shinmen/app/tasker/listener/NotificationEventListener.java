package io.shinmen.app.tasker.listener;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import io.shinmen.app.tasker.event.TaskEvent;
import io.shinmen.app.tasker.model.User;
import io.shinmen.app.tasker.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @Async
    @EventListener
    public void handleTaskEvent(TaskEvent event) {
        try {
            switch (event.getType()) {
                case TASK_CREATED:
                    notificationService.publishTaskCreated(event.getTask(), event.getActorUserId());
                    break;

                case TASK_UPDATED:
                    notificationService.publishTaskUpdated(
                        event.getTask(),
                        event.getActorUserId(),
                        "TASK_UPDATED",
                        event.getData().get("oldValue"),
                        event.getData().get("newValue")
                    );
                    break;

                case TASK_DELETED:
                    notificationService.publishTaskDeleted(event.getTask(), event.getActorUserId());
                    break;

                case TASK_ASSIGNED:
                    handleTaskAssignment(event);
                    break;

                case TASK_STATUS_CHANGED:
                    notificationService.publishTaskStatusChanged(
                        event.getTask(),
                        event.getActorUserId(),
                        event.getData().get("oldStatus"),
                        event.getData().get("newStatus")
                    );
                    break;

                case TASK_LOCKED:
                    notificationService.publishTaskLocked(event.getTask(), event.getActorUserId());
                    break;

                case TASK_UNLOCKED:
                    notificationService.publishTaskUnlocked(event.getTask(), event.getActorUserId());
                    break;

                case COMMENT_ADDED:
                    notificationService.publishCommentAdded(
                        event.getTask(),
                        (Long) event.getData().get("commentId"),
                        event.getActorUserId()
                    );
                    break;

                case WATCHER_ADDED:
                    User watcher = (User) event.getData().get("watcher");
                    notificationService.publishWatcherAdded(
                        event.getTask(),
                        watcher,
                        event.getActorUserId()
                    );
                    break;
            }
        } catch (Exception e) {
            log.error("Error processing task event notification", e);
        }
    }

    private void handleTaskAssignment(TaskEvent event) {
        User oldAssignee = (User) event.getData().get("oldAssignee");
        User newAssignee = (User) event.getData().get("newAssignee");

        notificationService.publishTaskAssigned(
            event.getTask(),
            oldAssignee,
            newAssignee,
            event.getActorUserId()
        );
    }
}
