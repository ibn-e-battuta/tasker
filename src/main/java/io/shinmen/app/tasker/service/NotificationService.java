package io.shinmen.app.tasker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import io.shinmen.app.tasker.event.TaskEvent;
import io.shinmen.app.tasker.event.TaskEventPublisher;
import io.shinmen.app.tasker.model.MentionNotification;
import io.shinmen.app.tasker.model.Task;
import io.shinmen.app.tasker.model.TaskComment;
import io.shinmen.app.tasker.model.TaskNotification;
import io.shinmen.app.tasker.model.User;
import io.shinmen.app.tasker.notifiication.NotificationDispatcher;
import io.shinmen.app.tasker.notifiication.SessionTracker;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService implements TaskEventPublisher {

    private final NotificationDispatcher notificationDispatcher;
    private final EmailService emailService;
    private final UserService userService;
    private final SessionTracker sessionTracker;

    public void registerUserSession(Long userId, String sessionId) {
        sessionTracker.registerSession(userId, sessionId);
    }

    public void removeUserSession(Long userId, String sessionId) {
        sessionTracker.removeSession(userId, sessionId);
    }

    @Override
    public void publishTaskCreated(Task task, Long actorUserId) {
        TaskNotification notification = buildTaskNotification(
            task,
            actorUserId,
            "TASK_CREATED",
            null,
            null,
            Map.of("taskName", task.getName())
        );

        notifyTeamMembers(task.getTeam().getId(), notification, actorUserId);

        if (task.getAssignedUser() != null) {
            emailService.sendTaskAssignmentNotification(task.getAssignedUser(), task);
        }
    }

    @Override
    public void publishTaskUpdated(Task task, Long actorUserId, String action,
            Object oldValue, Object newValue) {
        TaskNotification notification = buildTaskNotification(
            task,
            actorUserId,
            action,
            oldValue,
            newValue,
            Map.of("taskName", task.getName())
        );

        notifyTaskParticipants(task, notification, actorUserId);
    }

    @Override
    public void publishTaskDeleted(Task task, Long actorUserId) {
        TaskNotification notification = buildTaskNotification(
            task,
            actorUserId,
            "TASK_DELETED",
            null,
            null,
            Map.of("taskName", task.getName())
        );

        notifyTaskParticipants(task, notification, actorUserId);
        notifyTaskDeletion(task, actorUserId);
    }

    @Override
    public void publishTaskAssigned(Task task, User oldAssignee, User newAssignee, Long actorUserId) {
        TaskNotification notification = buildTaskNotification(
            task,
            actorUserId,
            "TASK_ASSIGNED",
            oldAssignee != null ? oldAssignee.getId() : null,
            newAssignee != null ? newAssignee.getId() : null,
            Map.of(
                "taskName", task.getName(),
                "oldAssigneeName", oldAssignee != null ?
                    oldAssignee.getFirstName() + " " + oldAssignee.getLastName() : "Unassigned",
                "newAssigneeName", newAssignee != null ?
                    newAssignee.getFirstName() + " " + newAssignee.getLastName() : "Unassigned"
            )
        );

        notifyTaskParticipants(task, notification, actorUserId);

        if (oldAssignee != null) {
            emailService.sendTaskUnassignmentNotification(oldAssignee, task);
        }
        if (newAssignee != null) {
            emailService.sendTaskAssignmentNotification(newAssignee, task);
        }
    }

    @Override
    public void publishTaskStatusChanged(Task task, Long actorUserId, Object oldStatus,
            Object newStatus) {
        TaskNotification notification = buildTaskNotification(
            task,
            actorUserId,
            "STATUS_CHANGED",
            oldStatus,
            newStatus,
            Map.of("taskName", task.getName())
        );

        notifyTaskParticipants(task, notification, actorUserId);
    }

    @Override
    public void publishCommentAdded(Task task, Long commentId, Long actorUserId) {
        TaskComment comment = findComment(task, commentId);
        if (comment == null) return;

        TaskNotification notification = buildTaskNotification(
            task,
            actorUserId,
            "COMMENT_ADDED",
            null,
            commentId,
            Map.of(
                "taskName", task.getName(),
                "commentContent", comment.getContent()
            )
        );

        notifyTaskParticipants(task, notification, actorUserId);

        Set<User> mentionedUsers = processMentions(comment.getContent());
        notifyMentionedUsers(task, comment, mentionedUsers, actorUserId);
    }

    @Override
    public void publishTaskLocked(Task task, Long actorUserId) {
        TaskNotification notification = buildTaskNotification(
            task,
            actorUserId,
            "TASK_LOCKED",
            false,
            true,
            Map.of("taskName", task.getName())
        );

        notifyTaskParticipants(task, notification, actorUserId);
    }

    @Override
    public void publishTaskUnlocked(Task task, Long actorUserId) {
        TaskNotification notification = buildTaskNotification(
            task,
            actorUserId,
            "TASK_UNLOCKED",
            true,
            false,
            Map.of("taskName", task.getName())
        );

        notifyTaskParticipants(task, notification, actorUserId);
    }

    @Override
    public void publishWatcherAdded(Task task, User watcher, Long actorUserId) {
        TaskNotification notification = buildTaskNotification(
            task,
            actorUserId,
            "WATCHER_ADDED",
            null,
            watcher.getId(),
            Map.of(
                "taskName", task.getName(),
                "watcherName", watcher.getFirstName() + " " + watcher.getLastName()
            )
        );

        notifyTaskParticipants(task, notification, actorUserId);
        emailService.sendWatcherAddedNotification(watcher, task, actorUserId);
    }

    @Override
    public void publishWatcherRemoved(Task task, User watcher, Long actorUserId) {
        TaskNotification notification = buildTaskNotification(
            task,
            actorUserId,
            "WATCHER_REMOVED",
            watcher.getId(),
            null,
            Map.of(
                "taskName", task.getName(),
                "watcherName", watcher.getFirstName() + " " + watcher.getLastName()
            )
        );

        notifyTaskParticipants(task, notification, actorUserId);
    }

    @Override
    public void publish(TaskEvent event) {
        try {
            switch (event.getType()) {
                case TASK_CREATED:
                    publishTaskCreated(event.getTask(), event.getActorUserId());
                    break;
                case TASK_UPDATED:
                    Map<String, Object> data = event.getData();
                    publishTaskUpdated(
                        event.getTask(),
                        event.getActorUserId(),
                        (String) data.get("action"),
                        data.get("oldValue"),
                        data.get("newValue")
                    );
                    break;
                case TASK_DELETED:
                    publishTaskDeleted(event.getTask(), event.getActorUserId());
                    break;
                case TASK_ASSIGNED:
                    handleTaskAssignment(event);
                    break;
                case TASK_STATUS_CHANGED:
                    handleStatusChange(event);
                    break;
                case COMMENT_ADDED:
                    publishCommentAdded(
                        event.getTask(),
                        (Long) event.getData().get("commentId"),
                        event.getActorUserId()
                    );
                    break;
                case TASK_LOCKED:
                    publishTaskLocked(event.getTask(), event.getActorUserId());
                    break;
                case TASK_UNLOCKED:
                    publishTaskUnlocked(event.getTask(), event.getActorUserId());
                    break;
                case WATCHER_ADDED:
                    handleWatcherAdded(event);
                    break;
                case WATCHER_REMOVED:
                    handleWatcherRemoved(event);
                    break;
                default:
                    log.warn("Unhandled event type: {}", event.getType());
            }
        } catch (Exception e) {
            log.error("Error processing task event: {}", event, e);
        }
    }

    private void handleTaskAssignment(TaskEvent event) {
        Map<String, Object> data = event.getData();
        User oldAssignee = (User) data.get("oldAssignee");
        User newAssignee = (User) data.get("newAssignee");
        publishTaskAssigned(event.getTask(), oldAssignee, newAssignee, event.getActorUserId());
    }

    private void handleStatusChange(TaskEvent event) {
        Map<String, Object> data = event.getData();
        publishTaskStatusChanged(
            event.getTask(),
            event.getActorUserId(),
            data.get("oldStatus"),
            data.get("newStatus")
        );
    }

    private void handleWatcherAdded(TaskEvent event) {
        Map<String, Object> data = event.getData();
        User watcher = (User) data.get("watcher");
        publishWatcherAdded(event.getTask(), watcher, event.getActorUserId());
    }

    private void handleWatcherRemoved(TaskEvent event) {
        Map<String, Object> data = event.getData();
        User watcher = (User) data.get("watcher");
        publishWatcherRemoved(event.getTask(), watcher, event.getActorUserId());
    }

    private void notifyTeamMembers(Long teamId, Object notification, Long excludeUserId) {
        notificationDispatcher.broadcastToTeam(
            teamId,
            "/tasks",
            notification
        );
    }

    private void notifyTaskParticipants(Task task, Object notification, Long actorUserId) {
        task.getWatchers().forEach(watcher -> {
            if (!watcher.getId().equals(actorUserId) &&
                sessionTracker.isUserOnline(watcher.getId())) {
                notificationDispatcher.sendToUser(
                    watcher.getId(),
                    "/queue/task-updates",
                    notification
                );
            }
        });

        if (task.getAssignedUser() != null &&
            !task.getAssignedUser().getId().equals(actorUserId) &&
            sessionTracker.isUserOnline(task.getAssignedUser().getId())) {
            notificationDispatcher.sendToUser(
                task.getAssignedUser().getId(),
                "/queue/task-updates",
                notification
            );
        }
    }

    private void notifyTaskDeletion(Task task, Long actorUserId) {
        Set<User> usersToNotify = new HashSet<>(task.getWatchers());
        if (task.getAssignedUser() != null) {
            usersToNotify.add(task.getAssignedUser());
        }

        usersToNotify.stream()
            .filter(user -> !user.getId().equals(actorUserId))
            .forEach(user -> emailService.sendTaskDeletedNotification(user, task));
    }

    private TaskNotification buildTaskNotification(Task task, Long actorUserId,
            String action, Object oldValue, Object newValue, Map<String, Object> data) {
        User actor = userService.getUserById(actorUserId);
        return TaskNotification.builder()
                .taskId(task.getId())
                .action(action)
                .userId(actorUserId)
                .userFullName(actor.getFirstName() + " " + actor.getLastName())
                .oldValue(oldValue)
                .newValue(newValue)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private void notifyMentionedUsers(Task task, TaskComment comment,
            Set<User> mentionedUsers, Long actorUserId) {
        mentionedUsers.stream()
            .filter(user -> !user.getId().equals(actorUserId))
            .forEach(user -> {
                if (sessionTracker.isUserOnline(user.getId())) {
                    notificationDispatcher.sendToUser(
                        user.getId(),
                        "/queue/mentions",
                        buildMentionNotification(task, comment, user, actorUserId)
                    );
                }
                emailService.sendMentionNotification(user, task, comment);
            });
    }

    private MentionNotification buildMentionNotification(Task task, TaskComment comment,
            User mentionedUser, Long actorUserId) {
        User actor = userService.getUserById(actorUserId);
        return MentionNotification.builder()
                .taskId(task.getId())
                .commentId(comment.getId())
                .mentionedBy(actorUserId)
                .userId(mentionedUser.getId())
                .mentionedByFullName(actor.getFirstName() + " " + actor.getLastName())
                .content(comment.getContent())
                .timestamp(LocalDateTime.now())
                .build();
    }

    private Set<User> processMentions(String content) {
        return new HashSet<>();
    }

    private TaskComment findComment(Task task, Long commentId) {
        return null;
    }
}
