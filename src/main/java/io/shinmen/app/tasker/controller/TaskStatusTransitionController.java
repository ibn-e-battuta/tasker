package io.shinmen.app.tasker.controller;

import io.shinmen.app.tasker.dto.ApiResponse;
import io.shinmen.app.tasker.dto.TaskStatusTransitionRequest;
import io.shinmen.app.tasker.dto.TaskStatusTransitionResponse;
import io.shinmen.app.tasker.model.Task;
import io.shinmen.app.tasker.security.UserPrincipal;
import io.shinmen.app.tasker.service.TaskStatusTransitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/teams/{teamId}/workflow")
@RequiredArgsConstructor
public class TaskStatusTransitionController {

    private final TaskStatusTransitionService transitionService;

    @GetMapping("/transitions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TaskStatusTransitionResponse>> getTeamTransitions(
            @PathVariable Long teamId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        List<TaskStatusTransitionResponse> transitions = transitionService.getTeamTransitions(
                teamId, currentUser.getId());
        return ResponseEntity.ok(transitions);
    }

    @PutMapping("/transitions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TaskStatusTransitionResponse> updateTransition(
            @PathVariable Long teamId,
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody TaskStatusTransitionRequest request) {
        TaskStatusTransitionResponse transition = transitionService.updateTransition(
                teamId, currentUser.getId(), request);
        return ResponseEntity.ok(transition);
    }

    @PutMapping("/final-status/{status}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> markStatusAsFinal(
            @PathVariable Long teamId,
            @PathVariable Task.TaskStatus status,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        transitionService.markStatusAsFinal(teamId, currentUser.getId(), status);
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Status marked as final successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }
}
