package io.shinmen.app.tasker.controller;

import io.shinmen.app.tasker.dto.ApiResponse;
import io.shinmen.app.tasker.dto.TeamLabelRequest;
import io.shinmen.app.tasker.dto.TeamLabelResponse;
import io.shinmen.app.tasker.security.UserPrincipal;
import io.shinmen.app.tasker.service.LabelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/teams/{teamId}/labels")
@RequiredArgsConstructor
public class LabelController {

    private final LabelService labelService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TeamLabelResponse>> getTeamLabels(
            @PathVariable Long teamId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        List<TeamLabelResponse> labels = labelService.getTeamLabels(teamId, currentUser.getId());
        return ResponseEntity.ok(labels);
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TeamLabelResponse> createLabel(
            @PathVariable Long teamId,
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody TeamLabelRequest request) {
        TeamLabelResponse label = labelService.createLabel(teamId, currentUser.getId(), request);
        return ResponseEntity.ok(label);
    }

    @PutMapping("/{labelId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TeamLabelResponse> updateLabel(
            @PathVariable Long teamId,
            @PathVariable Long labelId,
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody TeamLabelRequest request) {
        TeamLabelResponse label = labelService.updateLabel(teamId, labelId,
                currentUser.getId(), request);
        return ResponseEntity.ok(label);
    }

    @DeleteMapping("/{labelId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> deleteLabel(
            @PathVariable Long teamId,
            @PathVariable Long labelId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        labelService.deleteLabel(teamId, labelId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Label deleted successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }
}
