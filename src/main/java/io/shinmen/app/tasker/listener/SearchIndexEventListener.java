package io.shinmen.app.tasker.listener;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import io.shinmen.app.tasker.event.TaskEvent;
import io.shinmen.app.tasker.service.TaskSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchIndexEventListener {

    private final TaskSearchService searchService;

    @Async
    @EventListener
    public void handleTaskEvent(TaskEvent event) {
        try {
            switch (event.getType()) {
                case TASK_CREATED:
                case TASK_UPDATED:
                case COMMENT_ADDED:
                case COMMENT_UPDATED:
                    searchService.indexTask(event.getTask());
                    break;

                case TASK_DELETED:
                    searchService.deleteTaskIndex(event.getTask().getId());
                    break;

                case COMMENT_DELETED:
                    searchService.indexTask(event.getTask());
                    break;
            }
        } catch (Exception e) {
            log.error("Error processing search index event", e);
        }
    }
}
