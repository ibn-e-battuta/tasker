package io.shinmen.app.tasker.controller;

import io.shinmen.app.tasker.dto.TaskSearchRequest;
import io.shinmen.app.tasker.dto.TaskSearchResponse;
import io.shinmen.app.tasker.security.UserPrincipal;
import io.shinmen.app.tasker.service.TaskSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/teams/{teamId}/tasks/search")
@RequiredArgsConstructor
public class TaskSearchController {

    private final TaskSearchService taskSearchService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<TaskSearchResponse>> searchTasks(
            @PathVariable Long teamId,
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestBody TaskSearchRequest request,
            Pageable pageable) {
        Page<TaskSearchResponse> results = taskSearchService.searchTasks(
                teamId, currentUser.getId(), request, pageable);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/suggest")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<String>> getSuggestions(
            @PathVariable Long teamId,
            @RequestParam String prefix) {
        List<String> suggestions = taskSearchService.getSuggestions(teamId, prefix);
        return ResponseEntity.ok(suggestions);
    }

    @GetMapping("/suggest/{field}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<String>> getFieldSuggestions(
            @PathVariable Long teamId,
            @PathVariable String field,
            @RequestParam String prefix) {
        List<String> suggestions = taskSearchService.getSuggestionsForField(teamId, field, prefix);
        return ResponseEntity.ok(suggestions);
    }
}
