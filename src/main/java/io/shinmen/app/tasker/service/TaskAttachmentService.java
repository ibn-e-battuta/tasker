package io.shinmen.app.tasker.service;

import io.shinmen.app.tasker.dto.TaskAttachmentResponse;
import io.shinmen.app.tasker.exception.CustomException;
import io.shinmen.app.tasker.model.Task;
import io.shinmen.app.tasker.model.TaskAttachment;
import io.shinmen.app.tasker.model.TeamPermission;
import io.shinmen.app.tasker.model.User;
import io.shinmen.app.tasker.repository.TaskAttachmentRepository;
import io.shinmen.app.tasker.repository.TaskRepository;
import io.shinmen.app.tasker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskAttachmentService {

    private final TaskAttachmentRepository attachmentRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskService taskService;
    private final EmailService emailService;

    @Value("${app.attachment.max-size}")
    private long maxFileSize;

    @Value("${app.attachment.upload-dir}")
    private String uploadDir;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif",
            "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain"
    );

    @Transactional
    public TaskAttachmentResponse addAttachment(Long taskId, Long userId, MultipartFile file) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new CustomException("Task not found", HttpStatus.NOT_FOUND));

        if (task.isLocked()) {
            throw new CustomException("Task is locked", HttpStatus.BAD_REQUEST);
        }

        taskService.validateTeamPermission(task.getTeam().getId(), userId, TeamPermission.ADD_ATTACHMENT);

        validateFile(file);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        TaskAttachment attachment = saveAttachment(task, user, file);

        taskService.recordTaskHistory(task, userId, "ATTACHMENT_ADDED", null, attachment.getId(),
                Map.of("fileName", attachment.getFileName()));

        notifyUsersAboutAttachment(task, attachment, userId);

        return mapAttachmentToResponse(attachment);
    }

    @Transactional
    public void deleteAttachment(Long attachmentId, Long userId) {
        TaskAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new CustomException("Attachment not found", HttpStatus.NOT_FOUND));

        if (attachment.getTask().isLocked()) {
            throw new CustomException("Task is locked", HttpStatus.BAD_REQUEST);
        }

        if (!attachment.getUser().getId().equals(userId) &&
                !attachment.getTask().getTeam().getOwner().getId().equals(userId)) {
            throw new CustomException("Insufficient permissions to delete attachment", HttpStatus.FORBIDDEN);
        }

        try {
            Files.deleteIfExists(Paths.get(attachment.getFilePath()));
        } catch (IOException e) {
            throw new CustomException("Failed to delete attachment file", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        taskService.recordTaskHistory(attachment.getTask(), userId, "ATTACHMENT_DELETED",
                attachment.getFileName(), null, null);

        attachmentRepository.delete(attachment);

        notifyUsersAboutAttachmentDeletion(attachment.getTask(), attachment, userId);
    }

    @Transactional(readOnly = true)
    public List<TaskAttachmentResponse> getTaskAttachments(Long taskId, Long userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new CustomException("Task not found", HttpStatus.NOT_FOUND));

        taskService.validateTeamPermission(task.getTeam().getId(), userId, TeamPermission.VIEW_ATTACHMENTS);

        List<TaskAttachment> attachments = attachmentRepository.findByTaskId(taskId);
        return attachments.stream()
                .map(this::mapAttachmentToResponse)
                .collect(Collectors.toList());
    }

    private void validateFile(MultipartFile file) {
        if (file.getSize() > maxFileSize) {
            throw new CustomException(
                    "File size exceeds maximum limit of " + maxFileSize / (1024 * 1024) + "MB",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new CustomException("Invalid file type", HttpStatus.BAD_REQUEST);
        }
    }

    private TaskAttachment saveAttachment(Task task, User user, MultipartFile file) {
        try {
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String uniqueFilename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            Path filePath = uploadPath.resolve(uniqueFilename);

            Files.copy(file.getInputStream(), filePath);

            TaskAttachment attachment = TaskAttachment.builder()
                    .task(task)
                    .user(user)
                    .fileName(file.getOriginalFilename())
                    .filePath(filePath.toString())
                    .fileType(file.getContentType())
                    .fileSize(file.getSize())
                    .build();

            return attachmentRepository.save(attachment);
        } catch (IOException e) {
            throw new CustomException("Failed to save attachment", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void notifyUsersAboutAttachment(Task task, TaskAttachment attachment, Long uploaderId) {
        Set<User> usersToNotify = new HashSet<>(task.getWatchers());
        if (task.getAssignedUser() != null) {
            usersToNotify.add(task.getAssignedUser());
        }
        usersToNotify.removeIf(user -> user.getId().equals(uploaderId));

        usersToNotify.forEach(user ->
                emailService.sendTaskAttachmentNotification(user, task, attachment));
    }

    private void notifyUsersAboutAttachmentDeletion(Task task, TaskAttachment attachment, Long deleterId) {
        Set<User> usersToNotify = new HashSet<>(task.getWatchers());
        if (task.getAssignedUser() != null) {
            usersToNotify.add(task.getAssignedUser());
        }
        usersToNotify.removeIf(user -> user.getId().equals(deleterId));

        usersToNotify.forEach(user ->
                emailService.sendTaskAttachmentDeletionNotification(user, task, attachment));
    }

    private TaskAttachmentResponse mapAttachmentToResponse(TaskAttachment attachment) {
        return TaskAttachmentResponse.builder()
                .id(attachment.getId())
                .fileName(attachment.getFileName())
                .fileType(attachment.getFileType())
                .fileSize(attachment.getFileSize())
                .user(taskService.mapUserToResponse(attachment.getUser()))
                .createdAt(attachment.getCreatedAt())
                .build();
    }
}
