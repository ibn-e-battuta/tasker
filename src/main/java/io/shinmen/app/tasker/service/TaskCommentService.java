package io.shinmen.app.tasker.service;

import io.shinmen.app.tasker.dto.TaskCommentRequest;
import io.shinmen.app.tasker.dto.TaskCommentResponse;
import io.shinmen.app.tasker.exception.CustomException;
import io.shinmen.app.tasker.model.Task;
import io.shinmen.app.tasker.model.TaskComment;
import io.shinmen.app.tasker.model.TeamPermission;
import io.shinmen.app.tasker.model.User;
import io.shinmen.app.tasker.repository.TaskCommentRepository;
import io.shinmen.app.tasker.repository.TaskRepository;
import io.shinmen.app.tasker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskCommentService {

    private final TaskCommentRepository commentRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskService taskService;
    private final EmailService emailService;

    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\w.]+)");

    @Transactional
    public TaskCommentResponse addComment(Long taskId, Long userId, TaskCommentRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new CustomException("Task not found", HttpStatus.NOT_FOUND));

        if (task.isLocked()) {
            throw new CustomException("Task is locked", HttpStatus.BAD_REQUEST);
        }

        taskService.validateTeamPermission(task.getTeam().getId(), userId, TeamPermission.ADD_COMMENT);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        TaskComment comment = TaskComment.builder()
                .task(task)
                .user(user)
                .content(request.getContent())
                .build();

        comment = commentRepository.save(comment);

        Set<User> mentionedUsers = processMentions(task, comment);

        taskService.recordTaskHistory(task, userId, "COMMENT_ADDED", null, comment.getId(), null);

        notifyUsersAboutComment(task, comment, mentionedUsers, userId);

        return mapCommentToResponse(comment);
    }

    @Transactional
    public TaskCommentResponse updateComment(Long commentId, Long userId, TaskCommentRequest request) {
        TaskComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException("Comment not found", HttpStatus.NOT_FOUND));

        if (comment.getTask().isLocked()) {
            throw new CustomException("Task is locked", HttpStatus.BAD_REQUEST);
        }

        if (!comment.getUser().getId().equals(userId)) {
            throw new CustomException("Only comment author can edit", HttpStatus.FORBIDDEN);
        }

        String oldContent = comment.getContent();
        comment.setContent(request.getContent());
        comment = commentRepository.save(comment);

        Set<User> mentionedUsers = processMentions(comment.getTask(), comment);

        taskService.recordTaskHistory(comment.getTask(), userId, "COMMENT_UPDATED",
                oldContent, comment.getContent(), null);

        notifyUsersAboutCommentUpdate(comment.getTask(), comment, mentionedUsers, userId);

        return mapCommentToResponse(comment);
    }

    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        TaskComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException("Comment not found", HttpStatus.NOT_FOUND));

        if (comment.getTask().isLocked()) {
            throw new CustomException("Task is locked", HttpStatus.BAD_REQUEST);
        }

        if (!comment.getUser().getId().equals(userId) &&
                !comment.getTask().getTeam().getOwner().getId().equals(userId)) {
            throw new CustomException("Insufficient permissions to delete comment", HttpStatus.FORBIDDEN);
        }

        taskService.recordTaskHistory(comment.getTask(), userId, "COMMENT_DELETED",
                comment.getContent(), null, null);

        commentRepository.delete(comment);

        notifyUsersAboutCommentDeletion(comment.getTask(), comment, userId);
    }

    @Transactional(readOnly = true)
    public List<TaskCommentResponse> getTaskComments(Long taskId, Long userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new CustomException("Task not found", HttpStatus.NOT_FOUND));

        taskService.validateTeamPermission(task.getTeam().getId(), userId, TeamPermission.VIEW_COMMENTS);

        List<TaskComment> comments = commentRepository.findByTaskIdOrderByCreatedAtDesc(taskId);
        return comments.stream()
                .map(this::mapCommentToResponse)
                .collect(Collectors.toList());
    }

    private Set<User> processMentions(Task task, TaskComment comment) {
        Matcher matcher = MENTION_PATTERN.matcher(comment.getContent());
        Set<User> mentionedUsers = new HashSet<>();

        while (matcher.find()) {
            String username = matcher.group(1);
            userRepository.findByEmail(username).ifPresent(user -> {
                if (taskService.isTeamMember(task.getTeam().getId(), user.getId())) {
                    mentionedUsers.add(user);
                }
            });
        }

        return mentionedUsers;
    }

    private void notifyUsersAboutComment(Task task, TaskComment comment,
                                         Set<User> mentionedUsers, Long commenterId) {
        Set<User> usersToNotify = new HashSet<>();

        usersToNotify.addAll(task.getWatchers());

        if (task.getAssignedUser() != null) {
            usersToNotify.add(task.getAssignedUser());
        }

        usersToNotify.addAll(mentionedUsers);

        usersToNotify.removeIf(user -> user.getId().equals(commenterId));

        usersToNotify.forEach(user ->
                emailService.sendTaskCommentNotification(user, task, comment));
    }

    private void notifyUsersAboutCommentUpdate(Task task, TaskComment comment,
                                               Set<User> newMentionedUsers, Long editorId) {
        Set<User> usersToNotify = new HashSet<>(task.getWatchers());
        if (task.getAssignedUser() != null) {
            usersToNotify.add(task.getAssignedUser());
        }
        usersToNotify.addAll(newMentionedUsers);
        usersToNotify.removeIf(user -> user.getId().equals(editorId));

        usersToNotify.forEach(user ->
                emailService.sendTaskCommentUpdateNotification(user, task, comment));
    }

    private void notifyUsersAboutCommentDeletion(Task task, TaskComment comment, Long deleterId) {
        Set<User> usersToNotify = new HashSet<>(task.getWatchers());
        if (task.getAssignedUser() != null) {
            usersToNotify.add(task.getAssignedUser());
        }
        usersToNotify.removeIf(user -> user.getId().equals(deleterId));

        usersToNotify.forEach(user ->
                emailService.sendTaskCommentDeletionNotification(user, task, comment));
    }

    private TaskCommentResponse mapCommentToResponse(TaskComment comment) {
        return TaskCommentResponse.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .user(taskService.mapUserToResponse(comment.getUser()))
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .version(comment.getVersion())
                .build();
    }
}
