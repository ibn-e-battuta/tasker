package io.shinmen.app.tasker.controller;

import io.shinmen.app.tasker.dto.ApiResponse;
import io.shinmen.app.tasker.dto.StatusReversionRequest;
import io.shinmen.app.tasker.dto.WorkflowConfigRequest;
import io.shinmen.app.tasker.security.UserPrincipal;
import io.shinmen.app.tasker.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/teams/{teamId}/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @PutMapping("/config")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> configureWorkflow(
            @PathVariable Long teamId,
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody WorkflowConfigRequest request) {
        workflowService.configureWorkflow(teamId, currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Workflow configuration updated successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PostMapping("/tasks/{taskId}/revert-status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> revertTaskStatus(
            @PathVariable Long teamId,
            @PathVariable Long taskId,
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody StatusReversionRequest request) {
        workflowService.revertTaskStatus(taskId, currentUser.getId(), request.getNewStatus());
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Task status reverted successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }
}
