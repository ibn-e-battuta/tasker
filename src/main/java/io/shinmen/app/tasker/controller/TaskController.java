package io.shinmen.app.tasker.controller;

import io.shinmen.app.tasker.dto.ApiResponse;
import io.shinmen.app.tasker.dto.TaskCreateRequest;
import io.shinmen.app.tasker.dto.TaskResponse;
import io.shinmen.app.tasker.dto.TaskUpdateRequest;
import io.shinmen.app.tasker.model.Task;
import io.shinmen.app.tasker.security.UserPrincipal;
import io.shinmen.app.tasker.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/teams/{teamId}/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TaskResponse> createTask(
            @PathVariable Long teamId,
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody TaskCreateRequest request) {
        TaskResponse task = taskService.createTask(teamId, currentUser.getId(), request);
        return ResponseEntity.ok(task);
    }

    @PutMapping("/{taskId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TaskResponse> updateTask(
            @PathVariable Long teamId,
            @PathVariable Long taskId,
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody TaskUpdateRequest request) {
        TaskResponse task = taskService.updateTask(taskId, currentUser.getId(), request);
        return ResponseEntity.ok(task);
    }

    @DeleteMapping("/{taskId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> deleteTask(
            @PathVariable Long teamId,
            @PathVariable Long taskId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        taskService.deleteTask(taskId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Task deleted successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PutMapping("/{taskId}/lock")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> lockTask(
            @PathVariable Long teamId,
            @PathVariable Long taskId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        taskService.lockTask(taskId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Task locked successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PutMapping("/{taskId}/unlock")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> unlockTask(
            @PathVariable Long teamId,
            @PathVariable Long taskId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        taskService.unlockTask(taskId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Task unlocked successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<TaskResponse>> getTeamTasks(
            @PathVariable Long teamId,
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) Task.TaskStatus status,
            @RequestParam(required = false) Task.TaskPriority priority,
            @RequestParam(required = false) Long assignedUserId,
            @RequestParam(required = false) String label,
            Pageable pageable) {
        Page<TaskResponse> tasks = taskService.getTeamTasks(teamId, currentUser.getId(),
                status, priority, assignedUserId, label, pageable);
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/my-tasks")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TaskResponse>> getUserTasks(@AuthenticationPrincipal UserPrincipal currentUser) {
        List<TaskResponse> tasks = taskService.getUserTasks(currentUser.getId());
        return ResponseEntity.ok(tasks);
    }
}
