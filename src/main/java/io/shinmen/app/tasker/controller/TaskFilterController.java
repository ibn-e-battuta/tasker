package io.shinmen.app.tasker.controller;

import io.shinmen.app.tasker.dto.SavedFilter;
import io.shinmen.app.tasker.dto.TaskFilter;
import io.shinmen.app.tasker.dto.TaskResponse;
import io.shinmen.app.tasker.dto.TaskSortCriteria;
import io.shinmen.app.tasker.dto.TaskStatistics;
import io.shinmen.app.tasker.security.UserPrincipal;
import io.shinmen.app.tasker.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/teams/{teamId}/tasks/filters")
@RequiredArgsConstructor
public class TaskFilterController {

    private final TaskService taskService;

    @PostMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<TaskResponse>> searchTasks(
            @PathVariable Long teamId,
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestBody TaskFilter filter,
            @RequestParam(required = false) List<TaskSortCriteria> sort,
            Pageable pageable) {
        Page<TaskResponse> tasks = taskService.findTasks(
            teamId, currentUser.getId(), filter, sort, pageable);
        return ResponseEntity.ok(tasks);
    }

    @PostMapping("/statistics")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TaskStatistics> getTaskStatistics(
            @PathVariable Long teamId,
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestBody TaskFilter filter) {
        TaskStatistics statistics = taskService.getTaskStatistics(
            teamId, currentUser.getId(), filter);
        return ResponseEntity.ok(statistics);
    }

    @PostMapping("/save")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SavedFilter> saveFilter(
            @PathVariable Long teamId,
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam String filterName,
            @RequestBody TaskFilter filter) {
        SavedFilter savedFilter = taskService.saveUserFilter(
            teamId, currentUser.getId(), filterName, filter);
        return ResponseEntity.ok(savedFilter);
    }

    @GetMapping("/saved")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SavedFilter>> getSavedFilters(
            @PathVariable Long teamId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        List<SavedFilter> filters = taskService.getUserFilters(
            teamId, currentUser.getId());
        return ResponseEntity.ok(filters);
    }
}
