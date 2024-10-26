package io.shinmen.app.tasker.repository;

import io.shinmen.app.tasker.model.Task;
import io.shinmen.app.tasker.model.TaskComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {
        List<TaskComment> findByTaskIdOrderByCreatedAtDesc(Long taskId);

        @Query("SELECT tc FROM TaskComment tc WHERE tc.task.id = :taskId " +
                        "AND tc.createdAt > :timestamp")
        List<TaskComment> findNewComments(
                        @Param("taskId") Long taskId,
                        @Param("timestamp") LocalDateTime timestamp);

        List<TaskComment> findByTask(Task task);
}
