package io.shinmen.app.tasker.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import io.shinmen.app.tasker.model.Task;
import io.shinmen.app.tasker.model.TaskHistory;
import io.shinmen.app.tasker.model.User;
import io.shinmen.app.tasker.repository.mongo.TaskHistoryRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TaskHistoryService {

    private final TaskHistoryRepository historyRepository;
    private final UserService userService;

    public void recordCreation(Task task, Long userId) {
        TaskHistory history = TaskHistory.builder()
                .taskId(task.getId())
                .userId(userId)
                .action("TASK_CREATED")
                .timestamp(LocalDateTime.now())
                .additionalInfo(Map.of(
                    "taskName", task.getName(),
                    "teamId", task.getTeam().getId()
                ))
                .build();

        historyRepository.save(history);
    }

    public void recordDeletion(Task task, Long userId) {
        TaskHistory history = TaskHistory.builder()
                .taskId(task.getId())
                .userId(userId)
                .action("TASK_DELETED")
                .timestamp(LocalDateTime.now())
                .additionalInfo(Map.of(
                    "taskName", task.getName(),
                    "teamId", task.getTeam().getId(),
                    "status", task.getStatus(),
                    "assignedUserId", task.getAssignedUser() != null ?
                        task.getAssignedUser().getId() : null
                ))
                .build();

        historyRepository.save(history);
    }

    public void recordUpdate(Task task, Long userId, Map<String, Object> changes) {
        TaskHistory history = TaskHistory.builder()
                .taskId(task.getId())
                .userId(userId)
                .action("TASK_UPDATED")
                .field((String) changes.get("field"))
                .oldValue(changes.get("oldValue"))
                .newValue(changes.get("newValue"))
                .timestamp(LocalDateTime.now())
                .build();

        historyRepository.save(history);
    }

    public void recordAssignment(Task task, Long userId, Map<String, Object> data) {
        Long oldAssigneeId = (Long) data.get("oldAssigneeId");
        Long newAssigneeId = (Long) data.get("newAssigneeId");

        String oldAssigneeName = oldAssigneeId != null ?
            getUserFullName(oldAssigneeId) : "Unassigned";
        String newAssigneeName = newAssigneeId != null ?
            getUserFullName(newAssigneeId) : "Unassigned";

        TaskHistory history = TaskHistory.builder()
                .taskId(task.getId())
                .userId(userId)
                .action("TASK_ASSIGNED")
                .oldValue(oldAssigneeName)
                .newValue(newAssigneeName)
                .timestamp(LocalDateTime.now())
                .additionalInfo(Map.of(
                    "oldAssigneeId", oldAssigneeId != null ? oldAssigneeId : "null",
                    "newAssigneeId", newAssigneeId != null ? newAssigneeId : "null"
                ))
                .build();

        historyRepository.save(history);
    }

    public void recordStatusChange(Task task, Long userId, Map<String, Object> data) {
        TaskHistory history = TaskHistory.builder()
                .taskId(task.getId())
                .userId(userId)
                .action("STATUS_CHANGED")
                .oldValue(data.get("oldStatus"))
                .newValue(data.get("newStatus"))
                .timestamp(LocalDateTime.now())
                .build();

        historyRepository.save(history);
    }

    public void recordCommentAction(Task task, Long userId, String action, Map<String, Object> data) {
        TaskHistory history = TaskHistory.builder()
                .taskId(task.getId())
                .userId(userId)
                .action(action)
                .timestamp(LocalDateTime.now())
                .additionalInfo(Map.of(
                    "commentId", data.get("commentId"),
                    "content", data.get("content")
                ))
                .build();

        if (data.containsKey("oldContent")) {
            history.setOldValue(data.get("oldContent"));
            history.setNewValue(data.get("newContent"));
        }

        historyRepository.save(history);
    }

    public void recordWatcherAction(Task task, Long userId, String action, Map<String, Object> data) {
        Long watcherId = (Long) data.get("watcherId");
        String watcherName = getUserFullName(watcherId);

        TaskHistory history = TaskHistory.builder()
                .taskId(task.getId())
                .userId(userId)
                .action(action)
                .timestamp(LocalDateTime.now())
                .additionalInfo(Map.of(
                    "watcherId", watcherId,
                    "watcherName", watcherName
                ))
                .build();

        historyRepository.save(history);
    }

    public Page<TaskHistory> getTaskHistory(Long taskId, Pageable pageable) {
        return historyRepository.findByTaskIdOrderByTimestampDesc(taskId, pageable);
    }

    public List<TaskHistory> getRecentTaskHistory(Long taskId, LocalDateTime since) {
        return historyRepository.findByTaskIdAndTimestampAfterOrderByTimestampDesc(
            taskId,
            since
        );
    }

    public Map<String, Object> getTaskAuditSummary(Long taskId) {
        List<TaskHistory> history = historyRepository.findByTaskIdOrderByTimestampAsc(taskId);

        return Map.of(
            "createdAt", history.isEmpty() ? null : history.get(0).getTimestamp(),
            "createdBy", history.isEmpty() ? null : history.get(0).getUserId(),
            "totalChanges", history.size(),
            "lastModifiedAt", history.isEmpty() ? null :
                history.get(history.size() - 1).getTimestamp(),
            "lastModifiedBy", history.isEmpty() ? null :
                history.get(history.size() - 1).getUserId(),
            "statusChanges", countStatusChanges(history),
            "assigneeChanges", countAssigneeChanges(history)
        );
    }

    private String getUserFullName(Long userId) {
        User user = userService.getUserById(userId);
        return user.getFirstName() + " " + user.getLastName();
    }

    private int countStatusChanges(List<TaskHistory> history) {
        return (int) history.stream()
                .filter(h -> "STATUS_CHANGED".equals(h.getAction()))
                .count();
    }

    private int countAssigneeChanges(List<TaskHistory> history) {
        return (int) history.stream()
                .filter(h -> "TASK_ASSIGNED".equals(h.getAction()))
                .count();
    }
}
