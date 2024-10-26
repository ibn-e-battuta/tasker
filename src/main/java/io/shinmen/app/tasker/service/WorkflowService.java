package io.shinmen.app.tasker.service;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.shinmen.app.tasker.dto.WorkflowConfigRequest;
import io.shinmen.app.tasker.exception.CustomException;
import io.shinmen.app.tasker.interfaces.TaskHistoryOperations;
import io.shinmen.app.tasker.interfaces.TaskNotificationOperations;
import io.shinmen.app.tasker.interfaces.TaskPermissionOperations;
import io.shinmen.app.tasker.interfaces.TaskWorkflowOperations;
import io.shinmen.app.tasker.model.Task;
import io.shinmen.app.tasker.model.Team;
import io.shinmen.app.tasker.model.TeamPermission;
import io.shinmen.app.tasker.model.TeamWorkflowConfig;
import io.shinmen.app.tasker.repository.TaskRepository;
import io.shinmen.app.tasker.repository.TeamRepository;
import io.shinmen.app.tasker.repository.TeamWorkflowConfigRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkflowService implements TaskWorkflowOperations {
    private final TeamWorkflowConfigRepository workflowConfigRepository;
    private final TaskHistoryOperations taskHistoryOperations;
    private final TaskNotificationOperations notificationOperations;
    private final TaskPermissionOperations permissionOperations;
    private final TaskRepository taskRepository;
    private final TeamRepository teamRepository;

    @Transactional
    public void configureWorkflow(Long teamId, Long userId, WorkflowConfigRequest request) {
        permissionOperations.validateTeamPermission(teamId, userId, TeamPermission.MANAGE_WORKFLOW);

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException("Team not found", HttpStatus.NOT_FOUND));

        TeamWorkflowConfig config = workflowConfigRepository.findByTeamId(teamId)
                .orElse(TeamWorkflowConfig.builder().team(team).build());

        config.setFinalStatuses(request.getFinalStatuses());
        config.setAllowStatusReversion(request.isAllowStatusReversion());
        config.setStatusReversionRoles(request.getStatusReversionRoleIds());

        workflowConfigRepository.save(config);

        taskHistoryOperations.recordTaskHistory(null, userId, "WORKFLOW_CONFIGURED",
            null, null, Map.of(
                "teamId", teamId,
                "finalStatuses", request.getFinalStatuses(),
                "allowReversion", request.isAllowStatusReversion()
            ));
    }

    @Override
    public boolean isFinalStatus(Long teamId, Task.TaskStatus status) {
        return workflowConfigRepository.findByTeamId(teamId)
                .map(config -> config.getFinalStatuses().contains(status))
                .orElse(false);
    }

    @Override
    public void validateStatusTransition(Long teamId, Task.TaskStatus fromStatus, Task.TaskStatus toStatus) {
        TeamWorkflowConfig config = workflowConfigRepository.findByTeamId(teamId)
                .orElseThrow(() -> new CustomException("Workflow configuration not found", HttpStatus.NOT_FOUND));

        if (config.getFinalStatuses().contains(fromStatus)) {
            throw new CustomException(
                "Cannot transition from a final status. Status reversion required.",
                HttpStatus.BAD_REQUEST
            );
        }
    }

    @Transactional
    public void revertTaskStatus(Long taskId, Long userId, Task.TaskStatus newStatus) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new CustomException("Task not found", HttpStatus.NOT_FOUND));

        Task.TaskStatus oldStatus = task.getStatus();
        task.setStatus(newStatus);
        taskRepository.save(task);

        taskHistoryOperations.recordTaskHistory(task, userId, "STATUS_REVERTED",
            oldStatus, newStatus, Map.of("reason", "Status reversion by authorized user"));

        notificationOperations.notifyWatchers(task, userId, "TASK_STATUS_REVERTED");
    }
}
