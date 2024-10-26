package io.shinmen.app.tasker.interfaces;

import io.shinmen.app.tasker.model.TeamPermission;

public interface TaskPermissionOperations {
    void validateTeamPermission(Long teamId, Long userId, TeamPermission permission);
}
