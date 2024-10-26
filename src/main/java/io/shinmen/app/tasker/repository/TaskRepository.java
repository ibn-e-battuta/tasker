package io.shinmen.app.tasker.repository;

import io.shinmen.app.tasker.model.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {
    @Query("SELECT t FROM Task t WHERE t.team.id = :teamId " +
            "AND (:status IS NULL OR t.status = :status) " +
            "AND (:priority IS NULL OR t.priority = :priority) " +
            "AND (:assignedUserId IS NULL OR t.assignedUser.id = :assignedUserId) " +
            "AND (:label IS NULL OR :label MEMBER OF t.labels)")
    Page<Task> findTasksByTeam(
            @Param("teamId") Long teamId,
            @Param("status") Task.TaskStatus status,
            @Param("priority") Task.TaskPriority priority,
            @Param("assignedUserId") Long assignedUserId,
            @Param("label") String label,
            Pageable pageable
    );

    @Query("SELECT t FROM Task t JOIN t.watchers w WHERE w.id = :userId")
    List<Task> findTasksByWatcher(@Param("userId") Long userId);

    List<Task> findByAssignedUserId(Long userId);

    @Query("SELECT t FROM Task t WHERE t.team.id = :teamId " +
            "AND t.assignedUser.id = :userId")
    List<Task> findTasksByTeamAndAssignedUser(
            @Param("teamId") Long teamId,
            @Param("userId") Long userId
    );

    @Query("SELECT t FROM Task t WHERE t.assignedUser.id = :userId AND t.deleted = false")
    List<Task> findByAssignedUserIdAndDeletedFalse(Long userId);
}
