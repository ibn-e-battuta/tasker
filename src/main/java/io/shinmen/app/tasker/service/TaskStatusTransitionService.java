package io.shinmen.app.tasker.service;

import io.shinmen.app.tasker.dto.TaskStatusTransitionRequest;
import io.shinmen.app.tasker.dto.TaskStatusTransitionResponse;
import io.shinmen.app.tasker.exception.CustomException;
import io.shinmen.app.tasker.model.Task;
import io.shinmen.app.tasker.model.TaskStatusTransition;
import io.shinmen.app.tasker.model.Team;
import io.shinmen.app.tasker.model.TeamPermission;
import io.shinmen.app.tasker.repository.TaskStatusTransitionRepository;
import io.shinmen.app.tasker.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskStatusTransitionService {

    private final TaskStatusTransitionRepository transitionRepository;
    private final TeamRepository teamRepository;
    private final TaskService taskService;

    @Transactional
    public void initializeDefaultTransitions(Team team) {
        Task.TaskStatus[] statuses = Task.TaskStatus.values();
        for (Task.TaskStatus fromStatus : statuses) {
            for (Task.TaskStatus toStatus : statuses) {
                boolean allowed = isDefaultTransitionAllowed(fromStatus, toStatus);
                TaskStatusTransition transition = TaskStatusTransition.builder()
                        .team(team)
                        .fromStatus(fromStatus)
                        .toStatus(toStatus)
                        .allowed(allowed)
                        .build();
                transitionRepository.save(transition);
            }
        }
    }

    @Transactional
    public TaskStatusTransitionResponse updateTransition(Long teamId, Long userId,
                                                         TaskStatusTransitionRequest request) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException("Team not found", HttpStatus.NOT_FOUND));

        taskService.validateTeamPermission(teamId, userId, TeamPermission.MANAGE_WORKFLOW);

        TaskStatusTransition transition = transitionRepository
                .findByTeamIdAndFromStatusAndToStatus(
                        teamId, request.getFromStatus(), request.getToStatus())
                .orElseGet(() -> TaskStatusTransition.builder()
                        .team(team)
                        .fromStatus(request.getFromStatus())
                        .toStatus(request.getToStatus())
                        .build());

        transition.setAllowed(request.isAllowed());
        transition = transitionRepository.save(transition);

        taskService.recordTaskHistory(null, userId, "WORKFLOW_UPDATED",
                String.format("%s -> %s: %s", request.getFromStatus(), request.getToStatus(), !request.isAllowed()),
                String.format("%s -> %s: %s", request.getFromStatus(), request.getToStatus(), request.isAllowed()),
                Map.of("teamId", teamId));

        return mapTransitionToResponse(transition);
    }

    @Transactional(readOnly = true)
    public List<TaskStatusTransitionResponse> getTeamTransitions(Long teamId, Long userId) {
        taskService.validateTeamPermission(teamId, userId, TeamPermission.VIEW_TEAM_SETTINGS);

        List<TaskStatusTransition> transitions = transitionRepository.findByTeamId(teamId);
        return transitions.stream()
                .map(this::mapTransitionToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void markStatusAsFinal(Long teamId, Long userId, Task.TaskStatus status) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException("Team not found", HttpStatus.NOT_FOUND));

        taskService.validateTeamPermission(teamId, userId, TeamPermission.MANAGE_WORKFLOW);

        List<TaskStatusTransition> transitions = transitionRepository.findByTeamId(teamId);
        transitions.stream()
                .filter(t -> t.getFromStatus() == status)
                .forEach(t -> t.setAllowed(false));

        transitionRepository.saveAll(transitions);

        taskService.recordTaskHistory(null, userId, "STATUS_MARKED_AS_FINAL",
                null, status.toString(), Map.of("teamId", teamId));
    }

    private boolean isDefaultTransitionAllowed(Task.TaskStatus fromStatus, Task.TaskStatus toStatus) {
        switch (fromStatus) {
            case TODO:
                return toStatus == Task.TaskStatus.IN_PROGRESS;
            case IN_PROGRESS:
                return toStatus == Task.TaskStatus.REVIEW || toStatus == Task.TaskStatus.TODO;
            case REVIEW:
                return toStatus == Task.TaskStatus.DONE || toStatus == Task.TaskStatus.IN_PROGRESS;
            case DONE:
                return toStatus == Task.TaskStatus.ARCHIVED || toStatus == Task.TaskStatus.IN_PROGRESS;
            case ARCHIVED:
                return false;
            default:
                return false;
        }
    }

    private TaskStatusTransitionResponse mapTransitionToResponse(TaskStatusTransition transition) {
        return TaskStatusTransitionResponse.builder()
                .id(transition.getId())
                .fromStatus(transition.getFromStatus())
                .toStatus(transition.getToStatus())
                .allowed(transition.isAllowed())
                .build();
    }
}
