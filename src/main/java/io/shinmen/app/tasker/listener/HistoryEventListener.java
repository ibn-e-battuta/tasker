package io.shinmen.app.tasker.listener;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import io.shinmen.app.tasker.event.TaskEvent;
import io.shinmen.app.tasker.service.TaskHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class HistoryEventListener {

    private final TaskHistoryService historyService;

    @Async
    @EventListener
    public void handleTaskEvent(TaskEvent event) {
        try {
            switch (event.getType()) {
                case TASK_CREATED:
                    historyService.recordCreation(event.getTask(), event.getActorUserId());
                    break;

                case TASK_UPDATED:
                    historyService.recordUpdate(
                        event.getTask(),
                        event.getActorUserId(),
                        event.getData()
                    );
                    break;

                case TASK_DELETED:
                    historyService.recordDeletion(event.getTask(), event.getActorUserId());
                    break;

                case TASK_ASSIGNED:
                    historyService.recordAssignment(
                        event.getTask(),
                        event.getActorUserId(),
                        event.getData()
                    );
                    break;

                case TASK_STATUS_CHANGED:
                    historyService.recordStatusChange(
                        event.getTask(),
                        event.getActorUserId(),
                        event.getData()
                    );
                    break;

                case COMMENT_ADDED:
                case COMMENT_UPDATED:
                case COMMENT_DELETED:
                    historyService.recordCommentAction(
                        event.getTask(),
                        event.getActorUserId(),
                        event.getType().toString(),
                        event.getData()
                    );
                    break;

                case WATCHER_ADDED:
                case WATCHER_REMOVED:
                    historyService.recordWatcherAction(
                        event.getTask(),
                        event.getActorUserId(),
                        event.getType().toString(),
                        event.getData()
                    );
                    break;
            }
        } catch (Exception e) {
            log.error("Error recording task history", e);
        }
    }
}
