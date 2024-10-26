package io.shinmen.app.tasker.controller;

import io.shinmen.app.tasker.dto.ApiResponse;
import io.shinmen.app.tasker.dto.TaskAttachmentResponse;
import io.shinmen.app.tasker.security.UserPrincipal;
import io.shinmen.app.tasker.service.TaskAttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/tasks/{taskId}/attachments")
@RequiredArgsConstructor
public class TaskAttachmentController {

    private final TaskAttachmentService attachmentService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TaskAttachmentResponse> addAttachment(
            @PathVariable Long taskId,
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam("file") MultipartFile file) {
        TaskAttachmentResponse attachment = attachmentService.addAttachment(
                taskId, currentUser.getId(), file);
        return ResponseEntity.ok(attachment);
    }

    @DeleteMapping("/{attachmentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> deleteAttachment(
            @PathVariable Long taskId,
            @PathVariable Long attachmentId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        attachmentService.deleteAttachment(attachmentId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Attachment deleted successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TaskAttachmentResponse>> getTaskAttachments(
            @PathVariable Long taskId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        List<TaskAttachmentResponse> attachments = attachmentService.getTaskAttachments(
                taskId, currentUser.getId());
        return ResponseEntity.ok(attachments);
    }
}
