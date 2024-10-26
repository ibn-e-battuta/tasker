package io.shinmen.app.tasker.repository;

import io.shinmen.app.tasker.model.TaskAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Long> {
    List<TaskAttachment> findByTaskId(Long taskId);

    @Query("SELECT SUM(ta.fileSize) FROM TaskAttachment ta WHERE ta.task.id = :taskId")
    Long calculateTotalAttachmentSize(@Param("taskId") Long taskId);
}
